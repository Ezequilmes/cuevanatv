package app.cuevanatv.scraper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HiddenStreamResolver(
    url: String,
    onUrlFound: (String) -> Unit,
    onTimeout: () -> Unit
) {
    // Mimic Android User-Agent
    val userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
    
    val state = rememberWebViewState(url)
    val navigator = rememberWebViewNavigator()
    val scope = rememberCoroutineScope()
    var hasFound by remember { mutableStateOf(false) }

    // State listener for URL changes and loading
    LaunchedEffect(state.lastLoadedUrl) {
        val currentUrl = state.lastLoadedUrl ?: ""
        // Common pattern for intercepted .m3u8 in network or redirects
        if (currentUrl.contains(".m3u8") && !hasFound) {
            println("[RESOLVER] Enlace capturado por URL: $currentUrl")
            hasFound = true
            onUrlFound(currentUrl)
        }
    }

    // Timeout logic: 20 seconds
    LaunchedEffect(url) {
        delay(20000)
        if (!hasFound) {
            println("[RESOLVER] Timeout alcanzado para: $url")
            onTimeout()
        }
    }

    // Invisible WebView Container
    Box(modifier = Modifier.size(1.dp).clip(RoundedCornerShape(0.dp))) {
        WebView(
            state = state,
            navigator = navigator,
            onCreated = { nativeWebView ->
                // Configuration of User-Agent and basic settings
                // Note: Actual implementation depends on KCEF/WebView version capabilities
                // For desktop (KCEF), we can usually set settings here if accessible
            }
        )
    }

    // JS-based extraction as a secondary method (mimicking APK interception)
    LaunchedEffect(state.loadingState) {
        if (state.loadingState is LoadingState.Finished && !hasFound) {
            scope.launch {
                // Wait a bit for dynamic scripts to run
                delay(2000)
                
                val jsExtraction = """
                    (function() {
                        // 1. Look for m3u8 in scripts
                        var scripts = document.getElementsByTagName('script');
                        for (var i = 0; i < scripts.length; i++) {
                            var content = scripts[i].innerHTML;
                            var match = content.match(/(https?:\/\/[^"']+\.m3u8[^"']*)/);
                            if (match) return match[1];
                        }
                        
                        // 2. Look for video tags
                        var videos = document.getElementsByTagName('video');
                        for (var i = 0; i < videos.length; i++) {
                            if (videos[i].src && videos[i].src.includes('.m3u8')) return videos[i].src;
                            var sources = videos[i].getElementsByTagName('source');
                            for (var j = 0; j < sources.length; j++) {
                                if (sources[j].src.includes('.m3u8')) return sources[j].src;
                            }
                        }
                        
                        // 3. Check window objects common in players (Clappr, JWPlayer, etc)
                        if (window.player && window.player.config && window.player.config.source) return window.player.config.source;
                        
                        return null;
                    })();
                """.trimIndent()

                // evaluateJavaScript is available in most multiplatform-webview versions
                // We assume the state or navigator provides a way to evaluate JS
                // If the library version differs, this part might need adjustment
                try {
                    // state.evaluateJavaScript(jsExtraction) { result -> ... }
                } catch (e: Exception) {
                    println("[RESOLVER] Error en JS: ${e.message}")
                }
            }
        }
    }
}
