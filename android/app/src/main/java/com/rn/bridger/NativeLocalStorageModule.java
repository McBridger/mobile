package com.rn.bridger;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableMap;

public class NativeLocalStorageModule extends NativeLocalStorageSpec {

    private final SharedPreferences storage;

    @NonNull
    @Override
    public String getName() {
        return "NativeLocalStorage";
    }

    public NativeLocalStorageModule(ReactApplicationContext reactContext) {
        super(reactContext);
        storage = getReactApplicationContext()
            .getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
    }

    @Override
    public void setItem(String value, String key) {
        storage.edit().putString(key, value).apply();

        WritableMap result = Arguments.createMap();
        result.putString("key", key);
        result.putString("value", value);
        emitOnKeyAdded(result);
    }

    @Override
    public String getItem(String key) {
        return storage.getString(key, null);
    }

    @Override
    public void removeItem(String key) {
        storage.edit().remove(key).apply();
    }

    @Override
    public void clear() {
        storage.edit().clear().apply();
    }
}
