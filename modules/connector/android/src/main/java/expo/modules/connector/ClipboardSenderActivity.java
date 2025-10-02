package expo.modules.connector;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import java.util.Optional;

public class ClipboardSenderActivity extends Activity {

    private static final String TAG = "ClipboardSenderActivity";
    private boolean isClipboardProcessed = false;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        if (isClipboardProcessed) return;

        isClipboardProcessed = true;
        Log.d(TAG, "Activity has focus. Reading clipboard...");
        sendClipboardData();
        Log.d(TAG, "Processing complete. Finishing activity.");
        finish();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Activity created.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) return;

        Log.d(TAG, "Activity paused. Finishing now.");
        finish();
    }

    private void sendClipboardData() {
        String dataToSend = getClipboardText();
        if (dataToSend == null) {
            Log.w(TAG, "Clipboard is empty or contains non-text data.");
            showToast("Clipboard is empty.");
            return;
        }

        BleSingleton bleSingleton = BleSingleton.getInstance(this);

        try {
            bleSingleton.send(dataToSend);
            Log.i(TAG, "Clipboard data sent successfully: " + dataToSend);
            showToast("Clipboard data sent.");
        } catch (BleSingleton.NotConnectedException e) {
            Log.e(TAG, "Failed to send clipboard data: No device connected.", e);
            showToast("No device connected.");
        }
    }

    @Nullable
    private String getClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        return Optional.ofNullable(clipboard)
            .filter(ClipboardManager::hasPrimaryClip)
            .map(ClipboardManager::getPrimaryClip)
            .filter(clip -> clip.getItemCount() > 0)
            .map(clip -> clip.getItemAt(0).getText())
            .map(CharSequence::toString)
            .filter(text -> !text.isEmpty())
            .orElse(null);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}