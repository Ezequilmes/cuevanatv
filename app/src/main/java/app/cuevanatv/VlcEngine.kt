package app.cuevanatv

import android.content.Context
import org.videolan.libvlc.LibVLC

/**
 * Singleton para el motor LibVLC.
 * Evita la recreación constante del motor nativo, previniendo IllegalStateException y agotamiento de JNI.
 */
object VlcEngine {
    private var instance: LibVLC? = null

    fun getInstance(context: Context): LibVLC {
        return instance ?: synchronized(this) {
            instance ?: createInstance(context).also { instance = it }
        }
    }

    private fun createInstance(context: Context): LibVLC {
        val args = ArrayList<String>().apply {
            add("-vvv")
            add("--http-reconnect")
            add("--network-caching=5000")
            add("--no-gnutls-verify")
            add("--drop-late-frames")
            add("--skip-frames")
            add("--codec=mediacodec,all")
            add("--vout=android_display,any")
        }
        // Usar applicationContext para evitar memory leaks
        return LibVLC(context.applicationContext, args)
    }
}
