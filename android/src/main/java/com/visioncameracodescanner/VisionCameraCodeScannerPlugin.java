package com.visioncameracodescanner;

import static com.visioncameracodescanner.BarcodeConverter.convertToArray;
import static com.visioncameracodescanner.BarcodeConverter.convertToMap;
import com.visioncameracodescanner.BarcodeDetectorHelper;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.ReadableNativeArray;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Tasks;
import com.mrousavy.camera.frameprocessor.FrameProcessorPlugin;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.common.internal.ImageConvertUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.Matrix;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import java.util.Comparator;

import com.visioncameracodescanner.BarcodeDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

public class VisionCameraCodeScannerPlugin extends FrameProcessorPlugin {
  private Context context;
  private BarcodeScanner barcodeScanner = null;
  private int barcodeScannerFormatsBitmap = -1;
  // private BarcodeReader zxingBarcodeReader = null;
  private BarcodeDetectorHelper barcodeDetectorHelper = null;
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
      Barcode.FORMAT_AZTEC));

  @Override
  public Object callback(ImageProxy frame, Object[] params) {
    createBarcodeInstance(params);

    // BarcodeReader.Options options = new BarcodeReader.Options();
    // options.setTryRotate(true);
    // options.setTryHarder(true);
    // options.setTryInvert(true);
    // options.setTryDownscale(true);
    // zxingBarcodeReader = new BarcodeReader(options);

    @SuppressLint("UnsafeOptInUsageError")
    Image mediaImage = frame.getImage();

    if (mediaImage == null)
      return null;

    // Extract scannerOptions and typesArray from Object[] params
    ReadableNativeMap scannerOptionsMap = null;
    WritableNativeArray typesArray = new WritableNativeArray();

    if (params.length > 1 && params[1] instanceof ReadableNativeMap) {
      scannerOptionsMap = (ReadableNativeMap) params[1];

      if (scannerOptionsMap.hasKey("types") && scannerOptionsMap.getArray("types") != null) {
        ReadableArray types = scannerOptionsMap.getArray("types");
        for (int i = 0; i < types.size(); i++) {
          switch (types.getType(i)) {
            case String:
              typesArray.pushString(types.getString(i));
              break;
            case Number:
              typesArray.pushInt(types.getInt(i));
              break;
            case Boolean:
              typesArray.pushBoolean(types.getBoolean(i));
              break;
          }
        }
      }
    }

    // Rotation compensation
    int deviceRotation = getDeviceSurfaceRotation(this.context);
    int newRotation = 0;
    if (deviceRotation == 0)
      newRotation = 90;
    else if (deviceRotation == 1)
      newRotation = 0;
    else if (deviceRotation == 3)
      newRotation = 180;

    InputImage image = InputImage.fromMediaImage(mediaImage, newRotation);
    ArrayList<Task<List<Barcode>>> tasks = new ArrayList<>();

    // Optional inversion logic
    if (scannerOptionsMap != null && scannerOptionsMap.hasKey("checkInverted") &&
        scannerOptionsMap.getBoolean("checkInverted")) {
      try {
        Bitmap bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image);
        Bitmap invertedBitmap = this.invert(bitmap);
        InputImage invertedImage = InputImage.fromBitmap(invertedBitmap, 0);
        tasks.add(barcodeScanner.process(invertedImage));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    tasks.add(barcodeScanner.process(image));

    try {
      ArrayList<Barcode> barcodes = new ArrayList<>();
      for (Task<List<Barcode>> task : tasks) {
        barcodes.addAll(Tasks.await(task));
      }

      // List<Map<String, Object>> resultArray = new ArrayList<>();
      WritableNativeArray resultArray = new WritableNativeArray();
      Set<String> tempResultsTexts = new HashSet<>();

      for (Barcode barcode : barcodes) {
        if (barcode.getRawValue() != null && !barcode.getRawValue().trim().isEmpty()) {
          // resultArray.add(convertBarcode(barcode));
          resultArray.pushMap(convertBarcode(barcode));
          tempResultsTexts.add(barcode.getRawValue());
        }
      }

      // ZXing/AI fallback logic if needed
      if (scannerOptionsMap != null) {
        Bitmap bitmap = null;
        try {
          bitmap = ImageConvertUtils.getInstance().getUpRightBitmap(image);

          int detectorMode = scannerOptionsMap.hasKey("detectorMode")
              ? scannerOptionsMap.getInt("detectorMode")
              : 1;
          boolean shouldEnableClassification = scannerOptionsMap.hasKey("shouldEnableClassification")
              && scannerOptionsMap.getBoolean("shouldEnableClassification");
          boolean shouldEnableMultipleObjects = !scannerOptionsMap.hasKey("shouldEnableMultipleObjects")
              || scannerOptionsMap.getBoolean("shouldEnableMultipleObjects");
          String modelName = scannerOptionsMap.hasKey("modelName")
              ? scannerOptionsMap.getString("modelName")
              : "final_model";
          int modelImageSize = scannerOptionsMap.hasKey("modelImageSize")
              ? scannerOptionsMap.getInt("modelImageSize")
              : 1024;
          float threshold = scannerOptionsMap.hasKey("thresold")
              ? (float) scannerOptionsMap.getDouble("thresold")
              : 0.5f;

          barcodeDetectorHelper = new BarcodeDetectorHelper(
              modelName,
              detectorMode,
              shouldEnableClassification,
              shouldEnableMultipleObjects,
              modelImageSize,
              threshold,
              (ReactApplicationContext) context);

          List<Map<String, Object>> detections = barcodeDetectorHelper.detectFrameProcessor(bitmap);
          int outputObjectIndex = 1;

          if (!detections.isEmpty() && !isProcessingFallback) {
            isProcessingFallback = true;

            for (Map<String, Object> detection : detections) {
              float x1 = ((Number) detection.get("x1")).floatValue();
              float y1 = ((Number) detection.get("y1")).floatValue();
              float x2 = ((Number) detection.get("x2")).floatValue();
              float y2 = ((Number) detection.get("y2")).floatValue();
              float x3 = ((Number) detection.get("x3")).floatValue();
              float y3 = ((Number) detection.get("y3")).floatValue();
              float x4 = ((Number) detection.get("x4")).floatValue();
              float y4 = ((Number) detection.get("y4")).floatValue();
              float angle = ((Number) detection.get("angle")).floatValue();
              float confidence = ((Number) detection.get("confidence")).floatValue();
              String classId = (String) detection.get("classId");

              float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
              float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
              float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
              float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

              Map<String, Object> boundingBox = Map.of(
                  "left", (int) minX,
                  "top", (int) minY,
                  "right", (int) maxX,
                  "bottom", (int) maxY);

              List<Map<String, Object>> cornerPoints = List.of(
                  Map.of("x", (int) x1, "y", (int) y1),
                  Map.of("x", (int) x2, "y", (int) y2),
                  Map.of("x", (int) x3, "y", (int) y3),
                  Map.of("x", (int) x4, "y", (int) y4));

              boolean detectMarkerOnly = scannerOptionsMap.hasKey("detectMarkerOnly")
                  && scannerOptionsMap.getBoolean("detectMarkerOnly");

              if (!detectMarkerOnly) {
                for (int k = 0; k < 2; k++) {
                  Matrix matrix = new Matrix();
                  matrix.postRotate(k == 0 ? -angle : (70 - angle));
                  Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                      matrix, true);

                  // Scale down by 50%
                  int scaledWidth = rotatedBitmap.getWidth() / 8;
                  int scaledHeight = rotatedBitmap.getHeight() / 8;

                  rotatedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, scaledWidth,
                      scaledHeight, true);

                  // Local cpp zxing start:
                  // Step 1: Convert bitmap to grayscale byte array (Luminance image)
                  int width = rotatedBitmap.getWidth();
                  int height = rotatedBitmap.getHeight();
                  byte[] grayscaleBytes = new byte[width * height];

                  int[] pixels = new int[width * height];
                  rotatedBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

                  for (int i = 0; i < pixels.length; i++) {
                    int color = pixels[i];
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    // Luminance conversion (grayscale)
                    grayscaleBytes[i] = (byte) ((r * 299 + g * 587 + b * 114) / 1000);
                  }

                  // Step 2: Call native decoder
                  String result = BarcodeDecoder.nativeDecode(grayscaleBytes, width, height);

                  if (result != null && !result.isEmpty()) {
                    try {
                      JSONObject json = new JSONObject(result);

                      String rawValue = json.getString("text");
                      String format = json.getString("format");
                      JSONArray points = json.getJSONArray("points");

                      if (!tempResultsTexts.contains(rawValue)) {
                        tempResultsTexts.add(rawValue);

                        float zx1 = (float) points.getJSONObject(0).getDouble("x");
                        float zy1 = (float) points.getJSONObject(0).getDouble("y");
                        float zx2 = (float) points.getJSONObject(1).getDouble("x");
                        float zy2 = (float) points.getJSONObject(1).getDouble("y");
                        float zx3 = (float) points.getJSONObject(2).getDouble("x");
                        float zy3 = (float) points.getJSONObject(2).getDouble("y");
                        float zx4 = (float) points.getJSONObject(3).getDouble("x");
                        float zy4 = (float) points.getJSONObject(3).getDouble("y");

                        float zminX = Math.min(Math.min(zx1, zx2), Math.min(zx3, zx4));
                        float zminY = Math.min(Math.min(zy1, zy2), Math.min(zy3, zy4));
                        float zmaxX = Math.max(Math.max(zx1, zx2), Math.max(zx3, zx4));
                        float zmaxY = Math.max(Math.max(zy1, zy2), Math.max(zy3, zy4));

                        Map<String, Object> zBox = Map.of(
                            "left", (int) zminX,
                            "top", (int) zminY,
                            "right", (int) zmaxX,
                            "bottom", (int) zmaxY);

                        List<Map<String, Object>> zCorners = List.of(
                            Map.of("x", (int) zx1, "y", (int) zy1),
                            Map.of("x", (int) zx2, "y", (int) zy2),
                            Map.of("x", (int) zx3, "y", (int) zy3),
                            Map.of("x", (int) zx4, "y", (int) zy4));

                        Map<String, Object> content = Map.of(
                            "type", "5",
                            "data", rawValue);

                        WritableMap cppMap = Arguments.makeNativeMap(Map.of(
                            "rawValue", rawValue,
                            "displayValue", rawValue,
                            "format", format,
                            "angle", (int) angle,
                            "boundingBox", boundingBox,
                            "cornerPoints", cornerPoints,
                            // "boundingBox", zBox,
                            // "cornerPoints", zCorners,
                            "content", content));

                        resultArray.pushMap(cppMap);
                      }
                    } catch (Exception e) {
                      Log.e("ZXingScanner", "Error parsing native ZXing result: " + e.getMessage());
                    }
                  } else {
                    Log.d("ZXingScanner", "No barcode detected.");
                  }

                  if (rotatedBitmap != null && !rotatedBitmap.isRecycled()) {
                    rotatedBitmap.recycle();
                  }
                }
              } else {
                String fallbackValue = "123456_" + outputObjectIndex;
                if (!tempResultsTexts.contains(fallbackValue)) {
                  tempResultsTexts.add(fallbackValue);
                  Map<String, Object> content = Map.of("type", "5", "data", fallbackValue);

                  WritableMap fakeResult = Arguments.makeNativeMap(Map.of(
                      "rawValue", fallbackValue,
                      "displayValue", fallbackValue,
                      "format", "RSS_14",
                      "angle", (int) angle,
                      "boundingBox", boundingBox,
                      "cornerPoints", cornerPoints,
                      "content", content));
                  resultArray.pushMap(fakeResult);
                }
              }

              outputObjectIndex++;
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          isProcessingFallback = false;
        }
      }

      ReadableArray readableArray = (ReadableArray) resultArray;

      return resultArray;

    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  private void createBarcodeInstance(Object[] params) {
    if (params[0] instanceof ReadableNativeArray) {
      ReadableNativeArray rawFormats = (ReadableNativeArray) params[0];

      int formatsBitmap = 0;
      int formatsIndex = 0;
      int formatsSize = rawFormats.size();
      int[] formats = new int[formatsSize];

      for (int i = 0; i < formatsSize; i++) {
        int format = rawFormats.getInt(i);
        if (barcodeFormats.contains(format)) {
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
                    Arrays.copyOfRange(formats, 1, formatsIndex))
                .build());
        barcodeScannerFormatsBitmap = formatsBitmap;
      }
    } else {
      throw new IllegalArgumentException("Second parameter must be an Array");
    }
  }

  private WritableNativeMap convertContent(@NonNull Barcode barcode) {
    WritableNativeMap map = new WritableNativeMap();

    int type = barcode.getValueType();
    map.putInt("type", type);

    switch (type) {
      case Barcode.TYPE_UNKNOWN:
      case Barcode.TYPE_ISBN:
      case Barcode.TYPE_TEXT:
        map.putString("data", barcode.getRawValue());
        break;
      case Barcode.TYPE_CONTACT_INFO:
        map.putMap("data", convertToMap(barcode.getContactInfo()));
        break;
      case Barcode.TYPE_EMAIL:
        map.putMap("data", convertToMap(barcode.getEmail()));
        break;
      case Barcode.TYPE_PHONE:
        map.putMap("data", convertToMap(barcode.getPhone()));
        break;
      case Barcode.TYPE_SMS:
        map.putMap("data", convertToMap(barcode.getSms()));
        break;
      case Barcode.TYPE_URL:
        map.putMap("data", convertToMap(barcode.getUrl()));
        break;
      case Barcode.TYPE_WIFI:
        map.putMap("data", convertToMap(barcode.getWifi()));
        break;
      case Barcode.TYPE_GEO:
        map.putMap("data", convertToMap(barcode.getGeoPoint()));
        break;
      case Barcode.TYPE_CALENDAR_EVENT:
        map.putMap("data", convertToMap(barcode.getCalendarEvent()));
        break;
      case Barcode.TYPE_DRIVER_LICENSE:
        map.putMap("data", convertToMap(barcode.getDriverLicense()));
        break;
    }

    return map;
  }

  private WritableNativeMap convertBarcode(@NonNull Barcode barcode) {
    WritableNativeMap map = new WritableNativeMap();

    Rect boundingBox = barcode.getBoundingBox();
    if (boundingBox != null) {
      map.putMap("boundingBox", convertToMap(boundingBox));
    }

    Point[] cornerPoints = barcode.getCornerPoints();
    if (cornerPoints != null) {
      map.putArray("cornerPoints", convertToArray(cornerPoints));
    }

    String displayValue = barcode.getDisplayValue();
    if (displayValue != null) {
      map.putString("displayValue", displayValue);
    }

    String rawValue = barcode.getRawValue();
    if (rawValue != null) {
      map.putString("rawValue", rawValue);
    }

    map.putMap("content", convertContent(barcode));
    map.putInt("format", barcode.getFormat());

    return map;
  }

  // Bitmap Inversion https://gist.github.com/moneytoo/87e3772c821cb1e86415
  private Bitmap invert(Bitmap src) {
    int height = src.getHeight();
    int width = src.getWidth();

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint();

    ColorMatrix matrixGrayscale = new ColorMatrix();
    matrixGrayscale.setSaturation(0);

    ColorMatrix matrixInvert = new ColorMatrix();
    matrixInvert.set(new float[] {
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
      // This might still need an Activity context for the display associated with a
      // window.
      // If this code is running without an Activity context, getting rotation can be
      // tricky.
      // However, the WindowManager approach might still be the most straightforward
      // if you need the *default* display's rotation.

      // A more robust way if you have an Activity context:
      // WindowManager windowManager = (WindowManager)
      // context.getSystemService(Context.WINDOW_SERVICE);
      // if (context instanceof Activity) {
      // return ((Activity) context).getDisplay().getRotation();
      // } else {
      // // Fallback or handle error if specific display needed
      // }
      // Given the original code was using WindowManager, let's try to update that
      // path minimally
      // if an alternative isn't readily available in this context.
      // For now, let's assume the existing WindowManager way is chosen for
      // simplicity,
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
        // If you are sure you need the default display and can't use an Activity
        // context
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

  // VisionCameraCodeScannerPlugin() {
  // super("scanCodes");
  // }

  VisionCameraCodeScannerPlugin(ReactApplicationContext reactContext) {
    super("scanCodes");
    this.context = reactContext;
  }
}
