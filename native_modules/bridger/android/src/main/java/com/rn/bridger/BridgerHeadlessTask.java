package com.rn.bridger;

import android.content.Intent;
import android.os.Bundle;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import javax.annotation.Nullable;

public class BridgerHeadlessTask extends HeadlessJsTaskService {
    @Override
    protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            return new HeadlessJsTaskConfig(
                "BridgerHeadlessTask", // This name must match the JS registration
                Arguments.fromBundle(extras),
                5000, // Timeout for the task in milliseconds
                true  // Allow task to run in foreground
            );
        }
        return null;
    }
}