package com.rn.bridger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import android.util.Log;

public class BubbleActionReceiver extends BroadcastReceiver {

    private static final String TAG = "BubbleActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Log.d(TAG, "Received action: " + intent.getAction());

        switch (intent.getAction()) {
            case BubbleModule.ACTION_BUBBLE_CLICK:
                Toast.makeText(context, "bubble click", Toast.LENGTH_SHORT).show();
                break;
            case BubbleModule.ACTION_BUBBLE_LONG_CLICK:
                Toast.makeText(context, "bubble long click", Toast.LENGTH_SHORT).show();
                break;
            default:
                Log.w(TAG, "Unhandled action: " + intent.getAction());
                break;
        }
    }
}