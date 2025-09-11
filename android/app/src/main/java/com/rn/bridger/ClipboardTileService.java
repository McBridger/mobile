package com.rn.bridger; // Use your package name

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class ClipboardTileService extends TileService {
    private static final String TAG = "ClipboardSenderActivity";

    @Override
    public void onTileAdded() {
        super.onTileAdded();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();

        unlockAndRun(() -> {
            Log.d(TAG, "Tile clicked. Launching ClipboardSenderActivity.");
            Intent intent = new Intent(this, ClipboardSenderActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
        });
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel("Send Clipboard");
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_send_clipboard));
        tile.updateTile();        
    }
}