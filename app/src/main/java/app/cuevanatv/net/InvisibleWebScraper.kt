package app.cuevanatv.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class InvisibleWebScraper(private val context: Context) {

    private var webView: WebView? = null
    private var isFound = false
    private val TAG = "ScraperCUE"

    @SuppressLint("SetJavaScriptEnabled")
    fun fetchLiveStream(pageUrl: String, onLinkFound: (String) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            isFound = false
            webView = WebView(context)
            
            val settings = webView!!.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            
            // EL INGREDIENTE SECRETO: Permite que el video arranque sin interacción humana
            settings.mediaPlaybackRequiresUserGesture = false 
            
            // DISFRAZ: Navegador de escritorio
            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

            webView!!.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url.toString()

                    if (!isFound && url.contains(".m3u8", ignoreCase = true)) {
                        if (!isAds(url)) {
                            isFound = true
                            Log.d(TAG, "🎯 Master Link capturado en vivo: $url")
                            
                            Handler(Looper.getMainLooper()).post {
                                onLinkFound(url)
                                destroyScraper()
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "🌍 Web cargada. El reproductor debería iniciar automáticamente...")
                }
            }

            Log.d(TAG, "🔍 Iniciando Scraping Invisible en dispositivo: $pageUrl")
            webView!!.loadUrl(pageUrl)
        }
    }

    private fun isAds(url: String): Boolean {
        val ads = listOf("ads", "telemetry", "google", "doubleclick", "analytics")
        return ads.any { url.contains(it, ignoreCase = true) }
    }

    fun destroyScraper() {
        Handler(Looper.getMainLooper()).post {
            webView?.apply {
                stopLoading()
                webViewClient = null
                destroy()
            }
            webView = null
            Log.d(TAG, "♻️ WebView de fondo destruido.")
        }
    }
}
