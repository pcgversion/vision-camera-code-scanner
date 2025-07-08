package com.visioncameracodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import android.util.Log;

@SuppressWarnings("deprecation")
public class YuvToRgbConverter {
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB script;
    private Allocation inputAllocation;
    private Allocation outputAllocation;

    public YuvToRgbConverter(Context context) {
        Log.d("ZXingScan", "Constructor invoked...");
        try {
            rs = RenderScript.create(context);
            Log.d("ZXingScan", "RenderScript.create successful");
            script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
            Log.d("ZXingScan", "ScriptIntrinsicYuvToRGB.createsuccessful");
        } catch (Exception e) {
            Log.e("ZXingScan", "RenderScript init failed: " + e.getMessage(), e);
        }
    }

    public Bitmap yuvToRgb(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported image format");
        }

        Log.d("ZXingScan", "ACTUAL CONVERSION invoked");
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] jpegBytes = out.toByteArray();

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }
}
