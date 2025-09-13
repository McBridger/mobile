package com.rn.bridger; // Or your package name

import androidx.annotation.NonNull;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.BaseReactPackage;

import java.util.HashMap;
import java.util.Map;

public class BleBridgerPackage extends BaseReactPackage {

  @Override
  public NativeModule getModule(@NonNull String name, @NonNull ReactApplicationContext reactContext) {
    // Use a switch statement to handle all your modules in this package
    return switch (name) {
      case BleConnectorModule.NAME -> new BleConnectorModule(reactContext);
      case BleScannerModule.NAME -> new BleScannerModule(reactContext);
      case BubbleModule.NAME -> new BubbleModule(reactContext);
      default -> null;
    };
  }

  @NonNull
  @Override
  public ReactModuleInfoProvider getReactModuleInfoProvider() {
    return () -> {
      final Map<String, ReactModuleInfo> moduleInfos = new HashMap<>();
      
      // Add info for the BleConnectorModule
      moduleInfos.put(
          BleConnectorModule.NAME,
          new ReactModuleInfo(
              BleConnectorModule.NAME,
              "BleConnector", // The name of the module in JS
              false, // canOverrideExistingModule
              false, // needsEagerInit
              false, // isCxxModule
              true // isTurboModule
          ));
          
      // Add info for the BleScannerModule
      moduleInfos.put(
          BleScannerModule.NAME,
          new ReactModuleInfo(
              BleScannerModule.NAME,
              "BleScanner", // The name of the module in JS
              false, // canOverrideExistingModule
              false, // needsEagerInit
              false, // isCxxModule
              true // isTurboModule
          ));
          
      // Add info for the BubbleModule
      moduleInfos.put(
          BubbleModule.NAME,
          new ReactModuleInfo(
              BubbleModule.NAME,
              "NativeBubble", // The name of the module in JS
              false, // canOverrideExistingModule
              false, // needsEagerInit
              false, // isCxxModule
              true // isTurboModule
          ));

      return moduleInfos;
    };
  }
}