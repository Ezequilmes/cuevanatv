package app.cuevanatv

import android.content.Context
import org.videolan.libvlc.LibVLC

/**
 * Singleton para el motor LibVLC.
 * Evita la recreación constante del motor nativo, previniendo IllegalStateException y agotamiento de JNI.
 */
object VlcEngine {
    private var instance: Any? = null

    fun getInstance(context: android.content.Context): Any {
        val current = instance
        if (current != null) return current
        return synchronized(this) {
            val current2 = instance
            if (current2 != null) current2
            else {
                val newInstance = createInstance(context)
                instance = newInstance
                newInstance
            }
        }
    }

    private fun createInstance(context: android.content.Context): Any {
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
        return org.videolan.libvlc.LibVLC(context.applicationContext, args)
    }
}
