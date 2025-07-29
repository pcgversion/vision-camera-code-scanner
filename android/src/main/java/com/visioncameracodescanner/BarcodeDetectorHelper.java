package com.visioncameracodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import com.google.mlkit.vision.common.InputImage;
import com.facebook.react.bridge.ReactApplicationContext;
import com.google.android.odml.image.MlImage;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.MlImageAdapter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

public class BarcodeDetectorHelper {

    private float threshold = 0.5f;
    private int numThreads = 2;
    private int maxResults = 3;
    private int currentDelegate = 0;
    private int currentModel = 2;
    private String modelName;
    private final ReactApplicationContext context;
    private ImageProcessor reusableImageProcessor = null;
    private MlImageAdapter resuableMlImageAdapter = null;
    private ObjectDetector objectDetector = null;
    private Interpreter objectInterpreter = null;

    private int modelSize = 1024;
    private static final String[] LABELS = {"barcode", "unknown"};

    public BarcodeDetectorHelper(String modelName, Integer detectorMode, Boolean shouldEnableClassification, Boolean shouldEnableMultipleObjects, Integer modelImageSize, Float threshold,  ReactApplicationContext context) {
        this.modelName = modelName;
        this.context = context;
        this.modelSize = modelImageSize != null ? modelImageSize : 1024;
        this.threshold = threshold != null ? threshold : 0.5f;
        setUpObjectInterpreter();
    }

    public void clearObjectInterpreter() {
        if (objectInterpreter != null) {
            objectInterpreter.close();
            objectInterpreter = null;
        }
    }

    public void setUpObjectInterpreter() {
        try {
            clearObjectInterpreter();
            String fileName = modelName + ".tflite";
            File customDir = new File(context.getFilesDir(), "custom_models");
            File modelFile = new File(customDir, fileName);

            if (!modelFile.exists()) {
                System.out.println("Model file does not exist at: " + modelFile.getAbsolutePath());
            }

            FileInputStream fileInputStream = new FileInputStream(modelFile);
            FileChannel fileChannel = fileInputStream.getChannel();
            long declaredLength = fileChannel.size();
            MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, declaredLength);
            objectInterpreter = new Interpreter(modelBuffer);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public List<Map<String, Object>>detectFrameProcessor(Bitmap image) {

        if(objectInterpreter == null)
            setUpObjectInterpreter();

        try {
            long inferenceTime = SystemClock.uptimeMillis();

            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            //System.out.println("Model Size: "+this.modelSize);

            //Bitmap resizedBitmap = Bitmap.createScaledBitmap(image, this.modelSize, this.modelSize, false);
            if (reusableImageProcessor == null) {
                reusableImageProcessor = new ImageProcessor.Builder()
                    .add(new ResizeOp(this.modelSize, this.modelSize, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)) // Faster than BILINEAR
                    .add(new NormalizeOp(0, 255.0f))
                    .build();
            }

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(image);

            //TensorImage tensorImage = MlImageAdapter.createTensorImageFrom(image);
            // ImageProcessor imageProcessor = new ImageProcessor.Builder()
            //         .add(new ResizeOp(this.modelSize, this.modelSize, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            //         .add(new NormalizeOp(0f, 255f))
            //         .build();
            TensorImage processedImage = reusableImageProcessor.process(tensorImage);

            int[] outputShape = objectInterpreter.getOutputTensor(0).shape();
            // To print it, you can just add:
            //System.out.println("Model Output Shape: " +outputShape[2]);
            float[][][] output = new float[outputShape[0]][outputShape[1]][outputShape[2]];
            objectInterpreter.run(processedImage.getBuffer(), output);

            // Convert the output to a list of detections
            List<Map<String, Object>> detections = parseDetections(output, originalWidth, originalHeight);
            inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
            return detections;
            //return objectDetector.detect(processedImage);

        } catch (Exception e) {
            Log.d("BarcodeDetectorHelper", "Error during object detection.", e);
            return new ArrayList<>(); // Return empty list on error
        }
    }

    private List<Map<String, Object>> parseDetections(float[][][] output, int originalWidth, int originalHeight) {
        List<Map<String, Object>> detections = new ArrayList<>();
        for (int i = 0; i < output[0].length; i++) {
            if(i>10) break;
            float[] prediction = output[0][i];
            float confidence = prediction[4];
            int classIdx = (int) prediction[5];
            String classId = classIdx < LABELS.length ? LABELS[classIdx] : "unknown";

            if (confidence >= this.threshold) {
                float xPx = prediction[0] * this.modelSize;
                float yPx = prediction[1] * this.modelSize;
                float wPx = prediction[2] * this.modelSize;
                float hPx = prediction[3] * this.modelSize;
                float angleRad = prediction[6];
                List<PointF> corners = getRotatedBoxCorners(xPx, yPx, wPx, hPx, angleRad);
                List<PointF> mappedCorners = new ArrayList<>();
                for (PointF pt : corners) {
                    mappedCorners.add(mapToOriginalImage(pt, this.modelSize, this.modelSize, originalWidth, originalHeight));
                }

                float angleInDegrees = (float) Math.toDegrees(angleRad);
                //Log.d("BarcodeDetector "+ i, "Angle: " + angleRad + ", " + angleInDegrees);

                Map<String, Object> detection = new HashMap<>();
                detection.put("x1", mappedCorners.get(0).x);
                detection.put("y1", mappedCorners.get(0).y);
                detection.put("x2", mappedCorners.get(1).x);
                detection.put("y2", mappedCorners.get(1).y);
                detection.put("x3", mappedCorners.get(2).x);
                detection.put("y3", mappedCorners.get(2).y);
                detection.put("x4", mappedCorners.get(3).x);
                detection.put("y4", mappedCorners.get(3).y);
                detection.put("confidence", confidence);
                detection.put("classId", classId);
                detection.put("angle", angleInDegrees);

                detections.add(detection);


            }
        }
        return detections;
    }

    private List<PointF> getRotatedBoxCorners(float cx, float cy, float w, float h, float angleRad) {
        float cosA = (float) Math.cos(angleRad);
        float sinA = (float) Math.sin(angleRad);
        float hw = w / 2;
        float hh = h / 2;

        float[][] relCorners = {
                {-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}
        };

        List<PointF> result = new ArrayList<>();
        for (float[] c : relCorners) {
            float x = c[0];
            float y = c[1];
            float xRot = x * cosA - y * sinA + cx;
            float yRot = x * sinA + y * cosA + cy;
            result.add(new PointF(xRot, yRot));
        }
        return result;
    }

    private PointF mapToOriginalImage(PointF pt, int modelW, int modelH, int origW, int origH) {
        float scaleX = (float) origW / modelW;
        float scaleY = (float) origH / modelH;
        return new PointF(pt.x * scaleX, pt.y * scaleY);
    }

    public static class PointF {
        public float x, y;
        public PointF(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final int DELEGATE_CPU = 0;
    public static final int DELEGATE_GPU = 1;
    public static final int DELEGATE_NNAPI = 2;
    public static final int MODEL_EFFICIENTDETV0 = 0;
    public static final int MODEL_EFFICIENTDETV1 = 1;
    public static final int MODEL_EFFICIENTDETV2 = 2;
    public static final int MODEL_EFFICIENTDETV3 = 3;
    public static final int MODEL_EFFICIENTDETV4 = 4;
}
