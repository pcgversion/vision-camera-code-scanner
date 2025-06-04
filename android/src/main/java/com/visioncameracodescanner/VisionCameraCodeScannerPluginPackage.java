package com.visioncameracodescanner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.mrousavy.camera.frameprocessors.Frame;
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin;
import com.mrousavy.camera.frameprocessors.FrameProcessorPluginRegistry;
import com.mrousavy.camera.frameprocessors.VisionCameraProxy;

import java.util.Collections;
import java.util.List;

public class VisionCameraCodeScannerPluginPackage implements ReactPackage {
  static {
    FrameProcessorPluginRegistry.addFrameProcessorPlugin(
        "scanBarcodes", VisionCameraCodeScannerPlugin::new);
  }
  @NonNull
  @org.jetbrains.annotations.NotNull
  @Override
  public List<NativeModule> createNativeModules(@NonNull @org.jetbrains.annotations.NotNull ReactApplicationContext reactContext) {
    //FrameProcessorPlugin.register(new VisionCameraCodeScannerPlugin());
    return Collections.emptyList();
  }

  @NonNull
  @org.jetbrains.annotations.NotNull
  @Override
  public List<ViewManager> createViewManagers(@NonNull @org.jetbrains.annotations.NotNull ReactApplicationContext reactContext) {
    return Collections.emptyList();
  }
}