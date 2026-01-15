package expo.modules.connector.core

import android.content.Context
import android.os.PowerManager
import android.util.Log
import expo.modules.connector.interfaces.IWakeManager

class WakeManager(context: Context) : IWakeManager {
    private val TAG = "WakeManager"
    
    // Ensure we use applicationContext to avoid memory leaks
    private val appContext = context.applicationContext
    
    private val wakeLock: PowerManager.WakeLock by lazy {
        (appContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bridger:SyncLock")
            .apply { setReferenceCounted(false) }
    }

    override fun acquire(durationMs: Long) {
        try {
            Log.v(TAG, "Acquiring WakeLock for ${durationMs}ms")
            wakeLock.acquire(durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    override fun release() {
        try {
            if (wakeLock.isHeld) {
                Log.v(TAG, "Releasing WakeLock")
                wakeLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    override suspend fun <T> withLock(durationMs: Long, block: suspend () -> T): T {
        acquire(durationMs)
        return try {
            block()
        } finally {
            release()
        }
    }
}
