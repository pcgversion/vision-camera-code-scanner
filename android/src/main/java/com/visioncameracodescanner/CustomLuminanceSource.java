package com.visioncameracodescanner;

import android.graphics.Bitmap;
import com.google.zxing.LuminanceSource;

public class CustomLuminanceSource extends LuminanceSource {

    private final byte[] luminances;
    private final int width;
    private final int height;

    public CustomLuminanceSource(Bitmap bitmap) {
        super(bitmap.getWidth(), bitmap.getHeight());

        width = bitmap.getWidth();
        height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        luminances = new byte[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            luminances[i] = (byte) ((r + g + b) / 3);
        }
    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        if (y < 0 || y >= height) {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        System.arraycopy(luminances, y * width, row, 0, width);
        return row;
    }

    @Override
    public byte[] getMatrix() {
        return luminances;
    }

    @Override
    public boolean isRotateSupported() {
        return true;
    }

    
}
