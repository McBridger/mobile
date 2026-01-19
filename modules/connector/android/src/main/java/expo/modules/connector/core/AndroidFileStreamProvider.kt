package expo.modules.connector.core

import android.content.Context
import android.net.Uri
import expo.modules.connector.interfaces.IFileStreamProvider
import java.io.InputStream

class AndroidFileStreamProvider(private val context: Context) : IFileStreamProvider {
    override fun openStream(uriString: String): InputStream? {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))
        } catch (e: Exception) {
            null
        }
    }
}
