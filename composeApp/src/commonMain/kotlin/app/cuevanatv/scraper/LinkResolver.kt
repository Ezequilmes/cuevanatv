package app.cuevanatv.scraper

import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class LinkResolver {
    private val client = ApiClient.getClient()
    
    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    suspend fun resolveStreamUrl(sourcePageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            println("[Resolver] Intentando extraer link de: $sourcePageUrl")
            
            val request = Request.Builder()
                .url(sourcePageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://google.com/")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: ""
                
                val m3u8Regex = """https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""".toRegex()
                val match = m3u8Regex.find(html)
                
                if (match != null) {
                    return@withContext match.value
                }

                val doc = Jsoup.parse(html)
                val scripts = doc.select("script")
                for (script in scripts) {
                    val data = script.data()
                    if (data.contains(".m3u8")) {
                        val scriptMatch = m3u8Regex.find(data)
                        if (scriptMatch != null) {
                            return@withContext scriptMatch.value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[Resolver] Error: ${e.message}")
        }
        null
    }
}
