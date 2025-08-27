package com.visioncameracodescanner;

public class BarcodeDecoder {
    static {
        System.loadLibrary("barcode-decoder");
    }

    public static native String nativeDecode(byte[] yPlane, int width, int height);
}
