package com.visioncameracodescanner;

import static com.visioncameracodescanner.BarcodeConverter.convertToArray;
import static com.visioncameracodescanner.BarcodeConverter.convertToMap;
import com.mrousavy.camera.react.GraphicOverlay;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import android.graphics.Rect;
import android.media.Image;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableNativeArray;

import org.jetbrains.annotations.NotNull; // Add this import
import androidx.camera.core.ImageProxy;


import com.google.android.gms.tasks.Tasks;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.graphics.Matrix;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.mrousavy.camera.frameprocessors.Frame;
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin;
import com.mrousavy.camera.frameprocessors.VisionCameraProxy;
import com.mrousavy.camera.core.FrameInvalidError;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.*;

import android.view.Display;
import android.view.WindowManager;

// OpenCV core & image processing
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.Scalar;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.OpenCVLoader;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.core.RotatedRect;

import zxingcpp.BarcodeReader;

import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeMap;
public class VisionCameraCodeScannerPlugin extends FrameProcessorPlugin {
  
   static {
    if (!OpenCVLoader.initLocal()) {
      Log.e("OpenCV", "Failed to load OpenCV!");
    } else {
      Log.d("OpenCV", "OpenCV loaded successfully!");
    }
  }
  private Context context;
  private BarcodeScanner barcodeScanner = null;
  private int barcodeScannerFormatsBitmap = -1;
  private BarcodeReader zxingBarcodeReader = null;
  private MultiFormatReader reader = new MultiFormatReader();
  private BarcodeDetectorHelper barcodeDetectorHelper = null;
  private Matrix matrix = new Matrix();

  // Add this flag
  private boolean isProcessingFallback = false;
  private static final Set<Integer> barcodeFormats = new HashSet<>(Arrays.asList(
    Barcode.FORMAT_UNKNOWN,
    Barcode.FORMAT_ALL_FORMATS,
    Barcode.FORMAT_CODE_128,
    Barcode.FORMAT_CODE_39,
    Barcode.FORMAT_CODE_93,
    Barcode.FORMAT_CODABAR,
    Barcode.FORMAT_DATA_MATRIX,
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_ITF,
    Barcode.FORMAT_QR_CODE,
    Barcode.FORMAT_UPC_A,
    Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_PDF417,
    Barcode.FORMAT_AZTEC
  ));

  

  VisionCameraCodeScannerPlugin(@NonNull VisionCameraProxy proxy, @Nullable Map<String, Object> options) {
    super();
    this.context = proxy.getContext();
    Log.d("VisionCameraCodeScannerPlugin", "VisionCameraCodeScannerPlugin init with options: " + options);

    //init zxing-cpp barcode reader
    BarcodeReader.Options zxingOptions = new BarcodeReader.Options();
    zxingOptions.setTryRotate(true);
    zxingOptions.setTryHarder(true);
    zxingOptions.setTryInvert(true);
    zxingOptions.setTryDownscale(true);

    
    zxingBarcodeReader = new BarcodeReader(zxingOptions);
    
  }

  @Override
  public Object callback(@NotNull Frame frame, @Nullable Map<String, Object> params) {

    List<android.graphics.Point[]> resultCornerPoints = new ArrayList<>();
    List<Integer> formatType = new ArrayList<>();
    GraphicOverlay targetOverlay = com.mrousavy.camera.react.CameraView.getActiveBarcodeGraphicOverlay();
    ImageProxy imageProxy;

    try {
      imageProxy = frame.getImageProxy();
    } catch (FrameInvalidError e) {
      e.printStackTrace();
      return null;
    }

    
    WritableNativeArray typesArray = new WritableNativeArray();

    Object typesObj = params != null ? params.get("types") : null;
    Object optionsObj = params != null ? params.get("options") : null;

    Map<String, Object> scannerOptions = null;
    if (optionsObj instanceof ReadableNativeMap) {
      scannerOptions = ((ReadableNativeMap) optionsObj).toHashMap();
    } else if (optionsObj instanceof Map) {
      // Already a HashMap or Map
      scannerOptions = (Map<String, Object>) optionsObj;
    }


    int deviceRotation = getDeviceSurfaceRotation(this.context);
    //Log.d("CodeScanner:",  String.valueOf(deviceRotation));
    if (typesObj instanceof List) {
        List<?> typesList = (List<?>) typesObj;
        for (Object item : typesList) {
            if (item instanceof Number) {
                typesArray.pushInt(((Number) item).intValue());
            } else if (item instanceof String) {
                typesArray.pushString((String) item);
            } else if (item instanceof Boolean) {
                typesArray.pushBoolean((Boolean) item);
            }
            // Add other types if needed
        }
    }
    createBarcodeInstance(typesArray);
    var newRotation = 0;
    @SuppressLint("UnsafeOptInUsageError")
    Image mediaImage = imageProxy.getImage();
    if (mediaImage != null) {
      
      ArrayList<Task<List<Barcode>>> tasks = new ArrayList<Task<List<Barcode>>>();


      if(deviceRotation == 0)
        newRotation = 90;
      if(deviceRotation == 1)
        newRotation = 0;
      if(deviceRotation == 3)
        newRotation = 180;


      InputImage image = InputImage.fromMediaImage(mediaImage, newRotation);
      Bitmap bitmap = null;

      Bitmap tempBitmap = convertImageProxyToBitmap(imageProxy);
      bitmap = rotateBitmap(tempBitmap, newRotation);

      boolean detectMarkerOnly = true;
      boolean showNativeOverlay = false;

      if(scannerOptions != null && scannerOptions.containsKey("detectMarkerOnly"))
        detectMarkerOnly = scannerOptions.get("detectMarkerOnly") != null ? (boolean) scannerOptions.get("detectMarkerOnly") : true;

      if(scannerOptions != null && scannerOptions.containsKey("showNativeOverlay"))
        showNativeOverlay = scannerOptions.get("showNativeOverlay") != null ? (boolean) scannerOptions.get("showNativeOverlay") : false;


      if (scannerOptions != null && scannerOptions.containsKey("checkInverted")) {
        Object checkInvertedObj = scannerOptions.get("checkInverted");
        boolean checkInverted = false;
        if (checkInvertedObj instanceof Boolean) {
            checkInverted = (Boolean) checkInvertedObj;
        }
        if (checkInverted) {
          //Bitmap bitmap = null;
          try {
            //bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image);
            Bitmap invertedBitmap = this.invert(bitmap);
            InputImage invertedImage = InputImage.fromBitmap(invertedBitmap, 0);
            tasks.add(barcodeScanner.process(invertedImage));
          } catch (Exception e) {
            e.printStackTrace();
            return null;
          }
        }

      }
      tasks.add(barcodeScanner.process(image));
    
      try {
        ArrayList<Barcode> barcodes = new ArrayList<Barcode>();
        for (Task<List<Barcode>> task : tasks) {
          barcodes.addAll(Tasks.await(task));
        }

        List<Map<String, Object>> resultArray = new ArrayList<>();
        if (targetOverlay != null && showNativeOverlay) {
          targetOverlay.setImageWidth(bitmap.getWidth());
          targetOverlay.setImageHeight(bitmap.getHeight());
          // targetOverlay.setMirrored(...); // If isMirrored is a property with a setter
          // targetOverlay.clear();
        }
        for (Barcode barcode : barcodes) {
          if (barcode.getRawValue() != null && !barcode.getRawValue().trim().isEmpty()) {
            resultArray.add(convertBarcode(barcode));
            if(showNativeOverlay) {
              android.graphics.Point[] points = barcode.getCornerPoints();
              if (points != null) {
                resultCornerPoints.add(points); // Call your conversion method
                formatType.add(barcode.getFormat());
              }
            }
          }

        }




        //we have to run AI Model against the frame
        //detect there is any plu code is present or not
        if ( scannerOptions != null )
        {
          //Bitmap bitmap = null;
          try {
            //bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image);
            var detectorMode = scannerOptions.get("detectorMode") != null ?  scannerOptions.get("detectorMode") : 1;
            var shouldEnableClassification = scannerOptions.get("shouldEnableClassification") != null ?  scannerOptions.get("shouldEnableClassification") : false;
            var shouldEnableMultipleObjects = scannerOptions.get("shouldEnableMultipleObjects") != null ? scannerOptions.get("shouldEnableMultipleObjects") : true;  
            var modelName = scannerOptions.get("modelName") != null ?  scannerOptions.get("modelName") : "yolo11-obb-od";
            Integer modelImageSize  = scannerOptions.get("modelImageSize") != null ?  ((Number) scannerOptions.get("modelImageSize")).intValue() : 1024;
            var threshold = scannerOptions.get("threshold") != null ?  ((Number) scannerOptions.get("threshold")).floatValue()  : 0.5f;
            if(barcodeDetectorHelper == null)
            barcodeDetectorHelper = new BarcodeDetectorHelper(
                modelName.toString(),
                detectorMode instanceof Integer ? (Integer) detectorMode : 1,
                shouldEnableClassification instanceof Boolean ? (Boolean) shouldEnableClassification : false,
                shouldEnableMultipleObjects instanceof Boolean ? (Boolean) shouldEnableMultipleObjects : true,
                modelImageSize,
                threshold,
                (ReactApplicationContext) context
            );
            // Log.d(
            //     "ZXingScan",
            //     "No barcodes found by ML Kit, trying ZXing..." +
            //     detectorMode + " " +
            //     shouldEnableClassification + " " +
            //     shouldEnableMultipleObjects + " " +
            //     modelName + " " +
            //     modelImageSize + " " +
            //     thresold
            // );
         
             //code to just save the original frame as bitmap iamge for debug purpose
//             File file = null;
//             try {
//               file = new File(context.getFilesDir(), "debug_bitmap.jpg");
//               FileOutputStream out = new FileOutputStream(file);
//               bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
//               out.close();
//               Log.d("ZXingScan", "Bitmap saved at.: " + file.getAbsolutePath());
//
//             } catch (IOException e) {
//               e.printStackTrace();
//             }
            
            
            //Lets perform if we got any plu codes
            try {

                //MlImage mlImage = new MediaMlImageBuilder(mediaImage).setRotation(newRotation).build();
                List<Map<String, Object>> pluCodeMarkers = barcodeDetectorHelper.detectFrameProcessor(bitmap);

                int outputObjectIndex = 1;

                if (pluCodeMarkers.size() > 0 && !isProcessingFallback) {
                  isProcessingFallback = true; // Set flag
                  for (Map<String, Object> detection : pluCodeMarkers)
                  {
                    float x1 = ((Number) detection.get("x1")).floatValue();
                    float y1 = ((Number) detection.get("y1")).floatValue();
                    float x2 = ((Number) detection.get("x2")).floatValue();
                    float y2 = ((Number) detection.get("y2")).floatValue();
                    float x3 = ((Number) detection.get("x3")).floatValue();
                    float y3 = ((Number) detection.get("y3")).floatValue();
                    float x4 = ((Number) detection.get("x4")).floatValue();
                    float y4 = ((Number) detection.get("y4")).floatValue();
                    float confidence = ((Number) detection.get("confidence")).floatValue();
                    String classId = (String) detection.get("classId");
                    float angle = ((Number) detection.get("angle")).floatValue();

                    //Compute min/max for the original points
                    float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
                    float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
                    float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
                    float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

                    Map<String, Object> boundingBox = new HashMap<>();
                    boundingBox.put("left", (int) minX);
                    boundingBox.put("top", (int) minY);
                    boundingBox.put("right", (int) maxX);
                    boundingBox.put("bottom", (int) maxY);

                    // cornerPoints
                    List<Map<String, Object>> cornerPoints = new ArrayList<>();

                    Map<String, Object> pt1 = new HashMap<>();
                    pt1.put("x",  (int) x1);
                    pt1.put("y",  (int) y1);
                    cornerPoints.add(pt1);

                    Map<String, Object> pt2 = new HashMap<>();
                    pt2.put("x",  (int) x2);
                    pt2.put("y",  (int) y2);
                    cornerPoints.add(pt2);

                    Map<String, Object> pt3 = new HashMap<>();
                    pt3.put("x",  (int) x3);
                    pt3.put("y",  (int) y3);
                    cornerPoints.add(pt3);

                    Map<String, Object> pt4 = new HashMap<>();
                    pt4.put("x",  (int) x4);
                    pt4.put("y",  (int) y4);

                    cornerPoints.add(pt4);

                    // Create Point[] for the current detection's corner points
                    android.graphics.Point[] currentPoints = new android.graphics.Point[4];
                    currentPoints[0] = new android.graphics.Point((int) x1, (int) y1);
                    currentPoints[1] = new android.graphics.Point((int) x2, (int) y2);
                    currentPoints[2] = new android.graphics.Point((int) x3, (int) y3);
                    currentPoints[3] = new android.graphics.Point((int) x4, (int) y4);

                    // Add these Point[] to the list that collects points for all detections
                    if(showNativeOverlay)
                    resultCornerPoints.add(currentPoints);

                    // enable below logic to crop bitmap
                    // Expand the rectangle by padding (in all directions)
                    // float padding = 150f; // or your desired value
                    // minX = Math.max(0, minX - padding);
                    // minY = Math.max(0, minY - padding);
                    // maxX = Math.min(bitmap.getWidth() - 1, maxX + padding);
                    // maxY = Math.min(bitmap.getHeight() - 1, maxY + padding);



                      Set<String> tempResultsTexts = new HashSet<>();
                      if (!detectMarkerOnly)
                      {
                        System.out.println("does it comes here");

                        int cropX = (int) minX;
                        int cropY = (int) minY;
                        int cropWidth = (int) (maxX - minX);
                        int cropHeight = (int) (maxY - minY);

                        // // Ensure valid crop size
                        // if (cropWidth > 0 && cropHeight > 0 &&
                        //     cropX >= 0 && cropY >= 0 &&
                        //     cropX + cropWidth <= bitmap.getWidth() &&
                        //     cropY + cropHeight <= bitmap.getHeight()) {
                        // Convert Bitmap to OpenCV Mat
                        Mat sourceMat = new Mat();
                        Utils.bitmapToMat(bitmap, sourceMat);

                        if (cropWidth <= 0 || cropHeight <= 0 || // If width or height is not positive
                                cropX < 0 || cropY < 0 ||             // If x or y is negative
                                (cropX + cropWidth) > sourceMat.cols() ||  // If cropped area exceeds mat width
                                (cropY + cropHeight) > sourceMat.rows()) { // If cropped area exceeds mat height

                          // Log the reason for skipping, if desired
                          continue; // Skip to the next iteration of the loop
                        }
                        // Define ROI and crop
                        org.opencv.core.Rect roi = new org.opencv.core.Rect(cropX, cropY, cropWidth, cropHeight);
                        Mat croppedMat = new Mat(sourceMat, roi).clone(); // clone to make it a separate copy

                        // Convert back to Bitmap
                        Bitmap croppedBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(croppedMat, croppedBitmap);

                        // Release Mats
                        sourceMat.release();
                        croppedMat.release();


                        //   //Save cropped bitmap for debugging
                        //   try {
                        //     File croppedFile = new File(context.getFilesDir(), "cropped_barcode.jpg");
                        //     FileOutputStream croppedOut = new FileOutputStream(croppedFile);
                        //     croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, croppedOut);
                        //     croppedOut.close();
                        //     Log.d("ZXingScan", "Cropped barcode saved at: " + croppedFile.getAbsolutePath());
                        //   } catch (IOException e) {
                        //     e.printStackTrace();
                        //   }
                        // }
                        // Use croppedBitmap as needed

                        for (var k = 0; k < 2; k++) {

                          //if (Math.abs(angle) > 15.0f) {
                          // ZXing fallback with rotation if needed
                          //Bitmap bitmapForZXing = croppedBitmap;
                          //matrix.reset();
                          // Rotate counter-clockwise by the angle

                          //matrix.postRotate(k == 0 ? -angle : (70 - angle)); // negative for counter-clockwise
                          // Create the rotated bitmap (may have transparent background)
                          //Bitmap bitmapForZXing = Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.getWidth(),croppedBitmap.getHeight(), matrix, true);
                          Bitmap bitmapForZXing = rotateBitmapOpenCV(croppedBitmap, k == 0 ? -angle : (70 - angle));

                          // File zfile = null;
                          // try {
                          //   zfile = new File(context.getFilesDir(), "debug_rbitmap_" + k + ".jpg");
                          //   FileOutputStream zout = new FileOutputStream(zfile);
                          //   bitmapForZXing.compress(Bitmap.CompressFormat.JPEG, 100, zout);
                          //   zout.close();
                          //   Log.d("ZXingScan", "Angled Bitmap saved at.: " + zfile.getAbsolutePath());

                          // } catch (IOException e) {
                          //   e.printStackTrace();
                          // }
                          // Log.d("ZXingScan " + k + ":", "Rotated bitmap by " + (angle) + " degrees for ZXing decode.");

                          //Note: After scanning start we scan rotated bitmaps against the zxing-cpp barcode reader
                          //We get result correct for barcode values, format, angle, type but cornerPoints and boundingBox
                          //values we can not use since its rotated image not the same as frame
                          Rect cropRect = new Rect(0, 0, bitmapForZXing.getWidth(), bitmapForZXing.getHeight());
                          List<BarcodeReader.Result> rzcppResults = zxingBarcodeReader.read(bitmapForZXing, cropRect,
                              0);
                          if (!rzcppResults.isEmpty()) {
                            //   Log.d("ZXingScan", "CPP Found " + zcppResults.size() + " barcodes with ZXing");
                            for (BarcodeReader.Result result : rzcppResults) {
                              // Log.d("ZXingCPPScanAngle", "zxing-cpp Decoded text at Angle " + k + ": " + result.getText());

                              // Process ZXing results as needed
                              // Log.d("ZXingScan","Decoded barcode at Angled " + k + ": " + rzcppResults.size() + " results found.");

                              BarcodeReader.Position position = result.getPosition();

                              float zx1 = ((Number) position.getTopLeft().x).floatValue();
                              float zy1 = ((Number) position.getTopLeft().y).floatValue();
                              float zx2 = ((Number) position.getTopRight().x).floatValue();
                              float zy2 = ((Number) position.getTopRight().y).floatValue();
                              float zx3 = ((Number) position.getBottomRight().x).floatValue();
                              float zy3 = ((Number) position.getBottomRight().y).floatValue();
                              float zx4 = ((Number) position.getBottomLeft().x).floatValue();
                              float zy4 = ((Number) position.getBottomRight().y).floatValue();

                              // Compute min/max for the original points
                              float zminX = Math.min(Math.min(zx1, zx2), Math.min(zx3, zx4));
                              float zminY = Math.min(Math.min(zy1, zy2), Math.min(zy3, zy4));
                              float zmaxX = Math.max(Math.max(zx1, zx2), Math.max(zx3, zx4));
                              float zmaxY = Math.max(Math.max(zy1, zy2), Math.max(zy3, zy4));

                              Map<String, Object> zboundingBox = new HashMap<>();
                              zboundingBox.put("left", (int) zminX);
                              zboundingBox.put("top", (int) zminY);
                              zboundingBox.put("right", (int) zmaxX);
                              zboundingBox.put("bottom", (int) zmaxY);

                              // cornerPoints
                              List<Map<String, Object>> zcornerPoints = new ArrayList<>();

                              Map<String, Object> zpt1 = new HashMap<>();
                              zpt1.put("x", (int) zx1);
                              zpt1.put("y", (int) zy1);
                              zcornerPoints.add(zpt1);

                              Map<String, Object> zpt2 = new HashMap<>();
                              zpt2.put("x", (int) zx2);
                              zpt2.put("y", (int) zy2);
                              zcornerPoints.add(zpt2);

                              Map<String, Object> zpt3 = new HashMap<>();
                              zpt3.put("x", (int) zx3);
                              zpt3.put("y", (int) zy3);
                              zcornerPoints.add(zpt3);

                              Map<String, Object> zpt4 = new HashMap<>();
                              zpt4.put("x", (int) zx4);
                              zpt4.put("y", (int) zy4);

                              zcornerPoints.add(zpt4);

                              String rawValue = (String) result.getText();
                              String displayValue = (String) result.getText();
                              String bFormat = "RSS_14";
                              String type = "5";
                              Map<String, Object> zcontentData = new HashMap<>();
                              zcontentData.put("type", type);
                              zcontentData.put("data", rawValue);

                              if (!tempResultsTexts.contains(rawValue)) {
                                tempResultsTexts.add(rawValue);
                                Map<String, Object> resultMap = new HashMap<>();
                                resultMap.put("rawValue", rawValue);
                                resultMap.put("displayValue", displayValue);
                                resultMap.put("format", bFormat);
                                resultMap.put("angle", (int) angle);
                                resultMap.put("boundingBox", zboundingBox);
                                resultMap.put("cornerPoints", zcornerPoints);
                                resultMap.put("content", zcontentData);
                                resultArray.add(resultMap);
                              }

                            }
                            if (bitmapForZXing != null && !bitmapForZXing.isRecycled()) {
                              bitmapForZXing.recycle();
                            }
                          }
                        }

                      } else {
                        //while only scanning need to feed fake plu barcodes info
                        // but cornerPoints, angle and boundingBox data is correct
                        // from the above pluMarkers
                        String resultText = "123456_" + outputObjectIndex;
                        String format = "RSS_14";
                        Map<String, Object> barcodeData = new HashMap<>();
                        barcodeData.put("data", resultText);
                        barcodeData.put("type", "5");
                        formatType.add(5);
                        if (!tempResultsTexts.contains(resultText)) {
                          tempResultsTexts.add(resultText);
                          Map<String, Object> resultMap = new HashMap<>();
                          resultMap.put("rawValue", resultText);
                          resultMap.put("displayValue", resultText);
                          resultMap.put("format", format);
                          resultMap.put("angle", (int) angle);
                          resultMap.put("boundingBox", boundingBox);
                          resultMap.put("cornerPoints", cornerPoints);
                          resultMap.put("content", barcodeData);
                          // Log.d("ZXingScan", "Adding ZXing Fake result: " + resultText + ", displayValue:" + resultText
                          //     + " format:" + format);
                          resultArray.add(resultMap);
                        }
                      }

                    // Log.d("Detection", "x1=" + x1 + ", y1=" + y1 + ", classId=" + classId + ", confidence=" + confidence
                    //     + ", angle=" + angle);
                    outputObjectIndex++;
                  }
              }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isProcessingFallback = false; // Reset flag
            }
            
            // List<String> zxingResults = decodeWithZXing(bitmap);
            
          }catch (Exception e) {
            e.printStackTrace();
          }
        }
        if(showNativeOverlay) {
          if (targetOverlay != null) {
            targetOverlay.post(new Runnable() {
              @Override
              public void run() {
                // Since this Runnable is posted from a non-null targetOverlay,
                // and executed on its UI thread, targetOverlay can be safely used here.
                targetOverlay.clear();
                var i = 0;
                for (android.graphics.Point[] points : resultCornerPoints) {
                  if (points != null) { // Added null checks for safety

                    // Assuming the constructor of TextBlockGraphic is public
                    // and matches these parameters.
                    // The "ocr" string needs to be handled if it's a parameter in your TextBlockGraphic constructor.
                    // If TextBlockGraphic constructor in Java is:
                    // TextBlockGraphic(GraphicOverlay overlay, Text.TextBlock block, String type)
                    var  tempBarcodeFormat = formatType.get(i);
                    Log.d("VCSPlugin", "test:" + tempBarcodeFormat);
                    targetOverlay.add(
                            new GraphicOverlay.TextBlockGraphic(targetOverlay, points, "barcode", 0)
                    );
                    i++;
                    // If TextBlockGraphic constructor in Java (matching your Kotlin example) is:
                    // TextBlockGraphic(GraphicOverlay overlay, Text.TextBlock block)
                    // Then you might not pass "ocr", or the "ocr" was a tag used elsewhere
                    // and not a constructor param for TextBlockGraphic directly.
                    // Let's assume your Kotlin version of TextBlockGraphic was
                    // class TextBlockGraphic(overlay: GraphicOverlay, private val textBlock: Text.TextBlock, private val type: String)
                    // then the Java call would be correct as above.
                  }
                }


              }
            });
          } else {
            Log.w("YourPluginTag", "targetOverlay is null, cannot post UI updates.");
          }
        }

        return resultArray;

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return null;
  }
  // MODIFY convertToArray to return WritableArray
  private WritableArray convertPointsToWritableArray(Point[] points) {
    WritableNativeArray array = new WritableNativeArray();
    if (points != null) {
      for (Point point : points) {
        WritableNativeMap map = new WritableNativeMap();
        map.putInt("x", (int) point.x);
        map.putInt("y", (int) point.y);
        array.pushMap(map);
      }
    }
    return array;
  }


  private void createBarcodeInstance(Object formatTypes) {
    if (formatTypes != null) {
      ReadableNativeArray rawFormats = (ReadableNativeArray) formatTypes;
      int formatsBitmap = 0;
      int formatsIndex = 0;
      int formatsSize = rawFormats.size();
      int[] formats = new int[formatsSize];
      for (int i = 0; i < formatsSize; i++) {
        int format = rawFormats.getInt(i);
        if (barcodeFormats.contains(format)){
          formats[formatsIndex] = format;
          formatsIndex++;
          formatsBitmap |= format;
        }
      }

      if (formatsIndex == 0) {
        throw new ArrayIndexOutOfBoundsException("Need to provide at least one valid Barcode format");
      }

      if (barcodeScanner == null || formatsBitmap != barcodeScannerFormatsBitmap) {
        barcodeScanner = BarcodeScanning.getClient(
          new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
              formats[0],
              Arrays.copyOfRange(formats, 1, formatsIndex)
            )
            .build());
        barcodeScannerFormatsBitmap = formatsBitmap;
      }
    } else {
      throw new IllegalArgumentException("Second parameter must be an Array");
    }
  }

  private Map<String, Object> convertContent(@NonNull Barcode barcode) {
    Map<String, Object> map = new HashMap<>();

    int type = barcode.getValueType();
    map.put("type", type);

    switch (type) {
      case Barcode.TYPE_UNKNOWN:
      case Barcode.TYPE_ISBN:
      case Barcode.TYPE_TEXT:
        map.put("data", barcode.getRawValue());
        break;
      case Barcode.TYPE_CONTACT_INFO:
        map.put("data", convertToMap(barcode.getContactInfo()));
        break;
      case Barcode.TYPE_EMAIL:
        map.put("data", convertToMap(barcode.getEmail()));
        break;
      case Barcode.TYPE_PHONE:
        map.put("data", convertToMap(barcode.getPhone()));
        break;
      case Barcode.TYPE_SMS:
        map.put("data", convertToMap(barcode.getSms()));
        break;
      case Barcode.TYPE_URL:
        map.put("data", convertToMap(barcode.getUrl()));
        break;
      case Barcode.TYPE_WIFI:
        map.put("data", convertToMap(barcode.getWifi()));
        break;
      case Barcode.TYPE_GEO:
        map.put("data", convertToMap(barcode.getGeoPoint()));
        break;
      case Barcode.TYPE_CALENDAR_EVENT:
        map.put("data", convertToMap(barcode.getCalendarEvent()));
        break;
      case Barcode.TYPE_DRIVER_LICENSE:
        map.put("data", convertToMap(barcode.getDriverLicense()));
        break;
    }

    return map;
  }

  private Map<String, Object> convertBarcode(@NonNull Barcode barcode) {
    Map<String, Object> map = new HashMap<>();

    Rect boundingBox = barcode.getBoundingBox();
    if (boundingBox != null) {
      map.put("boundingBox", convertToMap(boundingBox));
    }

    android.graphics.Point[] cornerPoints = barcode.getCornerPoints();
    if (cornerPoints != null) {
      map.put("cornerPoints", convertToArray(cornerPoints));
    }

    String displayValue = barcode.getDisplayValue();
    if (displayValue != null) {
      map.put("displayValue", displayValue);
    }

    String rawValue = barcode.getRawValue();
    if (rawValue != null) {
      map.put("rawValue", rawValue);
    }

    map.put("content", convertContent(barcode));
    map.put("format", barcode.getFormat());

    return map;
  }

  // Bitmap Inversion https://gist.github.com/moneytoo/87e3772c821cb1e86415
  private Bitmap invert(Bitmap src)
  {
    int height = src.getHeight();
    int width = src.getWidth();

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint();

    ColorMatrix matrixGrayscale = new ColorMatrix();
    matrixGrayscale.setSaturation(0);

    ColorMatrix matrixInvert = new ColorMatrix();
    matrixInvert.set(new float[]
    {
      -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
      0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
      0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
      0.0f, 0.0f, 0.0f, 1.0f, 0.0f
    });
    matrixInvert.preConcat(matrixGrayscale);

    ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrixInvert);
    paint.setColorFilter(filter);

    canvas.drawBitmap(src, 0, 0, paint);
    return bitmap;
  }

  public static int getDeviceSurfaceRotation(Context context) {
    if (context == null) {
      System.err.println("Error: Context cannot be null to get device surface rotation.");
      return -1;
    }
    // For API level 30 and above
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // You might need to adjust how you get the display based on where this context
      // comes from (Activity vs Application). For a general service/plugin context:
      // This might still need an Activity context for the display associated with a window.
      // If this code is running without an Activity context, getting rotation can be tricky.
      // However, the WindowManager approach might still be the most straightforward
      // if you need the *default* display's rotation.

      // A more robust way if you have an Activity context:
      // WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      // if (context instanceof Activity) {
      //    return ((Activity) context).getDisplay().getRotation();
      // } else {
      //    // Fallback or handle error if specific display needed
      // }
      // Given the original code was using WindowManager, let's try to update that path minimally
      // if an alternative isn't readily available in this context.
      // For now, let's assume the existing WindowManager way is chosen for simplicity,
      // but be aware of its deprecation. If you have an Activity context available,
      // that's often preferred.

      // If you *must* use WindowManager for some reason, and the API is available:
      // This might require more context about *which* display you need.
      // For the default display, the old method is what's deprecated.
      // The replacement is usually to get display from Context on API 30+
      // or from DisplayManager for specific displays.

      // Safest bet if context MIGHT NOT BE an Activity context,
      // and you need to stick to WindowManager for now (acknowledging deprecation):
      WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      if (windowManager != null) {
        // The deprecation warning will persist with getDefaultDisplay()
        // If you are sure you need the default display and can't use an Activity context
        // for DisplayManager or context.getDisplay(), you might live with the warning
        // or find a more specific way to get the display you need.
        @SuppressWarnings("deprecation") // Suppress if you must use it
        Display display = windowManager.getDefaultDisplay();
        if (display != null) {
          return display.getRotation();
        }
      }

    } else {
      // Legacy path for older APIs
      WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      if (windowManager != null) {
        @SuppressWarnings("deprecation")
        Display display = windowManager.getDefaultDisplay();
        if (display != null) {
          return display.getRotation();
        }
      }
    }
    System.err.println("Error: Could not retrieve WindowManager or Display service.");
    return -1;
  }
 
  
  //  private Bitmap convertImageProxyToBitmap(ImageProxy imageProxy) {
  //   @SuppressLint("UnsafeOptInUsageError")
  //   Image image = imageProxy.getImage();
  //   // Log.d("ZXingScan", "got the iamge");
  //   if (image == null) {
  //     // Log.d("ZXingScan", "returnign null");
  //     return null;
  //   }
  //   // Log.d("ZXingScan", "before conversion");
  //   YuvToRgbConverter yuvToRgbConverter = new YuvToRgbConverter(context);
  //   // Log.d("ZXingScan", "after conversion");
  //   return yuvToRgbConverter.yuvToRgb(image);
  // }

  @SuppressLint("UnsafeOptInUsageError")
  private Bitmap convertImageProxyToBitmap(ImageProxy imageProxy) {
    Image image = imageProxy.getImage();
    if (image == null) return null;

    int width = image.getWidth();
    int height = image.getHeight();

    ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
    ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
    ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

    int ySize = yBuffer.remaining();
    int uSize = uBuffer.remaining();
    int vSize = vBuffer.remaining();

    byte[] nv21 = new byte[ySize + uSize + vSize];

    // Fill NV21 byte array — VU order for NV21
    yBuffer.get(nv21, 0, ySize);
    vBuffer.get(nv21, ySize, vSize);
    uBuffer.get(nv21, ySize + vSize, uSize);

    // Convert to OpenCV Mat
    Mat yuvMat = new Mat(height + height / 2, width, CvType.CV_8UC1);
    yuvMat.put(0, 0, nv21);

    // Convert to RGB
    Mat rgbMat = new Mat();
    Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);

    // Convert to Bitmap
    Bitmap bitmap = Bitmap.createBitmap(rgbMat.cols(), rgbMat.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(rgbMat, bitmap);

    // Cleanup
    yuvMat.release();
    rgbMat.release();

    return bitmap;
  }


  private Bitmap rotateBitmap(Bitmap src, int angle) {
    Matrix matrix = new Matrix();
    matrix.postRotate(angle);
    return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
  }

  private Bitmap rotateBitmapOpenCV(Bitmap srcBitmap, float angleDegrees) {
    if (srcBitmap == null) return null;

    // Step 1: Convert Bitmap to Mat
    Mat srcMat = new Mat();
    Utils.bitmapToMat(srcBitmap, srcMat);

    // Step 2: Compute rotation matrix
    Point center = new Point(srcMat.cols() / 2.0, srcMat.rows() / 2.0);
    Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angleDegrees, 1.0);

    // Step 3: Compute bounding rectangle after rotation
    RotatedRect rotatedRect = new RotatedRect(center, srcMat.size(), angleDegrees);
    org.opencv.core.Rect bbox = rotatedRect.boundingRect();

    // Step 4: Adjust transformation to keep image centered
    double[] mat0 = rotationMatrix.get(0, 2);
    double[] mat1 = rotationMatrix.get(1, 2);
    rotationMatrix.put(0, 2, mat0[0] + bbox.width / 2.0 - center.x);
    rotationMatrix.put(1, 2, mat1[0] + bbox.height / 2.0 - center.y);

    // Step 5: Rotate the image
    Mat rotatedMat = new Mat();
    Imgproc.warpAffine(srcMat, rotatedMat, rotationMatrix, bbox.size());

    // Step 6: Convert back to Bitmap
    Bitmap rotatedBitmap = Bitmap.createBitmap(rotatedMat.cols(), rotatedMat.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(rotatedMat, rotatedBitmap);

    // Step 7: Release Mats
    srcMat.release();
    rotatedMat.release();
    rotationMatrix.release();

    return rotatedBitmap;
  }

  // Grok AI
  private static final String TAG = "ZXingScan";

  public static Bitmap preprocessImage(Bitmap bitmap, Context context) {
    Mat src = new Mat();
    Utils.bitmapToMat(bitmap, src);

    // Preprocess image
    Mat preprocessed = preprocessImage(src, context);
    if (preprocessed == null) {
      Log.d(TAG, "Preprocessing failed, returning original.");
      return bitmap;
    }

    // Align using contour-based method
    Mat aligned = alignUsingContours(src, preprocessed, context);
    if (aligned == null || aligned.empty()) {
      Log.d(TAG, "Alignment failed, returning original.");
      return bitmap;
    }

    // Convert aligned Mat to Bitmap
    Bitmap correctedBitmap = Bitmap.createBitmap(aligned.cols(), aligned.rows(), Bitmap.Config.ARGB_8888);
    Utils.matToBitmap(aligned, correctedBitmap);
    return correctedBitmap;
  }

  private static Mat preprocessImage(Mat src, Context context) {
    // Convert to grayscale
    Mat gray = new Mat();
    Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

    // Apply minimal Gaussian blur to reduce noise
    Mat blurred = new Mat();
    Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);

    // Try both binary and inverse binary thresholding
    Mat binary = new Mat();
    Mat binaryInv = new Mat();
    Imgproc.adaptiveThreshold(blurred, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 2);
    Imgproc.adaptiveThreshold(blurred, binaryInv, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
        15, 2);

    // Choose threshold with more barcode content
    double binaryCount = Core.countNonZero(binary);
    double binaryInvCount = Core.countNonZero(binaryInv);
    Mat thresh = (binaryCount > binaryInvCount) ? binaryInv : binary;

    // Morphological closing with larger kernel to connect stacked rows
    Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(7, 3));
    Mat morph = new Mat();
    Imgproc.morphologyEx(thresh, morph, Imgproc.MORPH_CLOSE, kernel);

    // Debug: Save preprocessed image
    try {
      String path = new File(context.getFilesDir(), "preprocessed.jpg").getAbsolutePath();
      Imgcodecs.imwrite(path, morph);
      Log.d(TAG, "Preprocessed image saved at: " + path);
    } catch (Exception e) {
      Log.e(TAG, "Failed to save preprocessed image: " + e.getMessage());
      try {
        Bitmap preprocessedBitmap = Bitmap.createBitmap(morph.cols(), morph.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(morph, preprocessedBitmap);
        File file = new File(context.getFilesDir(), "preprocessed_fallback.jpg");
        FileOutputStream out = new FileOutputStream(file);
        preprocessedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.close();
        Log.d(TAG, "Preprocessed fallback image saved at: " + file.getAbsolutePath());
      } catch (IOException ex) {
        Log.e(TAG, "Failed to save preprocessed fallback image: " + ex.getMessage());
      }
    }

    return morph;
  }

  private static Mat alignUsingContours(Mat src, Mat preprocessed, Context context) {
    List<MatOfPoint> contours = new ArrayList<>();
    Mat hierarchy = new Mat();
    Imgproc.findContours(preprocessed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

    if (contours.isEmpty()) {
      Log.d(TAG, "No contours found.");
      return null;
    }

    Log.d(TAG, "Number of contours detected: " + contours.size());

    MatOfPoint2f bestContour = null;
    double maxArea = 0;

    for (MatOfPoint contour : contours) {
      MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
      double peri = Imgproc.arcLength(contour2f, true);
      MatOfPoint2f approx = new MatOfPoint2f();
      Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true); // tighter epsilon

      double area = Imgproc.contourArea(new MatOfPoint(approx.toArray()));
      if (approx.total() == 4 && area > 1000 && Imgproc.isContourConvex(new MatOfPoint(approx.toArray()))) {
        if (area > maxArea) {
          maxArea = area;
          bestContour = approx;
        }
      }
    }

    Point[] points;
    if (bestContour != null) {
      points = bestContour.toArray();
      Log.d(TAG, "Found valid 4-point contour, area: " + maxArea);
    } else {
      Log.d(TAG, "No valid 4-point contour found. Falling back to RotatedRect.");
      // Fallback: use largest rotated rectangle
      RotatedRect largestRect = null;
      for (MatOfPoint contour : contours) {
        MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
        RotatedRect rect = Imgproc.minAreaRect(contour2f);
        if (rect.size.area() > maxArea) {
          maxArea = rect.size.area();
          largestRect = rect;
        }
      }

      if (largestRect == null) {
        Log.d(TAG, "No suitable fallback rectangle found.");
        return null;
      }

      points = new Point[4];
      largestRect.points(points);
    }

    // Debug: draw contour corners
    Mat debugImage = src.clone();
    for (Point p : points) {
      Imgproc.circle(debugImage, p, 5, new Scalar(0, 0, 255), -1);
    }
    try {
      String path = new File(context.getFilesDir(), "debug_contour_corners.jpg").getAbsolutePath();
      Imgcodecs.imwrite(path, debugImage);
      Log.d(TAG, "Debug contour corners saved at: " + path);
    } catch (Exception e) {
      Log.e(TAG, "Error saving debug corners image: " + e.getMessage());
    }

    // Order points
    if (points.length != 4) {
      Log.d(TAG, "Insufficient points even after fallback.");
      return null;
    }

    Point[] ordered = orderPoints(points);

    // Compute width and height
    double width = Math.max(
        distance(ordered[0], ordered[1]),
        distance(ordered[2], ordered[3]));
    double height = Math.max(
        distance(ordered[0], ordered[3]),
        distance(ordered[1], ordered[2]));

    if (width < 10 || height < 10) {
      Log.d(TAG, "Dimensions too small: width=" + width + ", height=" + height);
      return null;
    }

    // Perspective transform
    MatOfPoint2f srcPts = new MatOfPoint2f(ordered);
    MatOfPoint2f dstPts = new MatOfPoint2f(
        new Point(0, 0),
        new Point(width - 1, 0),
        new Point(width - 1, height - 1),
        new Point(0, height - 1));

    Mat M = Imgproc.getPerspectiveTransform(srcPts, dstPts);
    Mat aligned = new Mat();
    Imgproc.warpPerspective(src, aligned, M, new Size(width, height),
        Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));

    // Check final image
    if (aligned.empty() || Core.countNonZero(toGrayscale(aligned)) < 10) {
      Log.d(TAG, "Warped image is empty or nearly blank.");
      return null;
    }

    return aligned;
  }

  private static double distance(Point p1, Point p2) {
    return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
  }

  private static Mat toGrayscale(Mat input) {
    Mat gray = new Mat();
    if (input.channels() > 1) {
      Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY);
    } else {
      input.copyTo(gray);
    }
    return gray;
  }

  private static Point[] orderPoints(Point[] pts) {
    Point[] ordered = new Point[4];
    double[] sum = new double[4];
    double[] diff = new double[4];
    for (int i = 0; i < 4; i++) {
      sum[i] = pts[i].x + pts[i].y;
      diff[i] = pts[i].y - pts[i].x;
    }

    int tlIdx = 0, brIdx = 0, trIdx = 0, blIdx = 0;
    for (int i = 1; i < 4; i++) {
      if (sum[i] < sum[tlIdx])
        tlIdx = i;
      if (sum[i] > sum[brIdx])
        brIdx = i;
      if (diff[i] < diff[trIdx])
        trIdx = i;
      if (diff[i] > diff[blIdx])
        blIdx = i;
    }

    ordered[0] = pts[tlIdx]; // Top-left
    ordered[1] = pts[trIdx]; // Top-right
    ordered[2] = pts[brIdx]; // Bottom-right
    ordered[3] = pts[blIdx]; // Bottom-left
    return ordered;
  }

}

