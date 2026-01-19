package expo.modules.connector.interfaces

import java.io.InputStream

/**
 * Clean abstraction for file streaming to avoid leaky Context/ContentResolver in transport layers.
 */
interface IFileStreamProvider {
    fun openStream(uriString: String): InputStream?
}
