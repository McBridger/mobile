package com.rn.bridger;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.facebook.react.TurboReactPackage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BleScannerPackage extends TurboReactPackage {

    @Override
    public NativeModule getModule(@NonNull String name, @NonNull ReactApplicationContext reactContext) {
        if (name.equals(BleScannerModule.NAME)) {
            return new BleScannerModule(reactContext);
        }
        return null;
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> {
            final Map<String, ReactModuleInfo> moduleInfos = new HashMap<>();
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
            return moduleInfos;
        };
    }

}