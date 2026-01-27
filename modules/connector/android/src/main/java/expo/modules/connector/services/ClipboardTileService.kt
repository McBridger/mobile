package expo.modules.connector.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import expo.modules.connector.R // Correct import for your R class
import expo.modules.connector.ui.ClipboardActivity

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {
    private val TAG = "ClipboardTileService"

//    override fun onTileAdded() {
//        super.onTileAdded()
//    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

//    override fun onStopListening() {
//        super.onStopListening()
//    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        unlockAndRun {
            Log.d(TAG, "Tile clicked. Launching ClipboardActivity.")
            val intent = Intent(this, ClipboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
                startActivityAndCollapse(pendingIntent)
            } else {
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        
        val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
        val label = appInfo.metaData?.getString("expo.modules.connector.TILE_LABEL") ?: "Send Clipboard"
        
        tile.label = label
        tile.icon = Icon.createWithResource(this, R.drawable.ic_send_clipboard) // Use R.drawable
        tile.updateTile()        
    }
}