package app.cuevanatv.net

import android.annotation.SuppressLint
import android.util.Log
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

object SslBypass {

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    fun install() {
        try {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            
            Log.d("SslBypass", "🛡️ SSL Bypass Global Activo (Trust All)")
        } catch (e: Exception) {
            Log.e("SslBypass", "❌ Fallo al instalar SSL Bypass: ${e.message}")
        }
    }
}
