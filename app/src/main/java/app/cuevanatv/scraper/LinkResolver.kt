package app.cuevanatv.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.util.regex.Pattern
import android.util.Base64
import java.net.URI

class LinkResolver {
    private val DEFAULT_REFERER = "https://www2.gnula.one/."
    suspend fun resolvePlayableUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val visited = mutableSetOf<String>()
        val hostResolved = resolveByHost(pageUrl)
        hostResolved ?: resolveRecursive(pageUrl, visited, 0, pageUrl)
    }

    fun resolveByHost(pageUrl: String): String? {
        val u = pageUrl.lowercase()
        return when {
            u.contains("voe.sx") || u.contains("voe.") -> resolveVoe(pageUrl)
            u.contains("streamwish") || u.contains("wish") -> resolveStreamwish(pageUrl)
            u.contains("fembed") || u.contains("feurl") || u.contains("femax") -> resolveFembed(pageUrl)
            u.contains("ok.ru") || u.contains("ok.ru/video") -> resolveOkRu(pageUrl)
            else -> null
        }
    }

    private fun isPlayable(url: String): Boolean {
        val u = url.lowercase()
        return u.endsWith(".m3u8") || u.endsWith(".mp4") || u.contains(".m3u8")
    }

    private fun downloadHtml(url: String, referer: String? = null): String? {
        return try {
            val conn = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .ignoreContentType(true)
                .followRedirects(true)
            val useRef = referer ?: DEFAULT_REFERER
            conn.referrer(useRef)
            conn.header("Referer", useRef)
            originFrom(useRef)?.let { conn.header("Origin", it) }
            val doc = conn.get()
            doc.outerHtml()
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveStreamwish(url: String): String? {
        val html = downloadHtml(url) ?: return null
        val direct = findFirstPlayable(html)
        if (direct != null) return direct
        val fileRegex = Pattern.compile("\"file\"\\s*:\\s*\"(https?:[^\"']+?\\.(?:m3u8|mp4)[^\"]*)\"")
        val mf = fileRegex.matcher(html)
        if (mf.find()) return mf.group(1).replace("\\/".toRegex(), "/")
        val srcRegex = Pattern.compile("(https?://[^\"'\\s]+?(?:m3u8|mp4)[^\"'\\s]*)")
        val ms = srcRegex.matcher(html)
        if (ms.find()) return ms.group(1)
        return null
    }

    private fun resolveVoe(url: String): String? {
        val html = downloadHtml(url) ?: return null
        val direct = findFirstPlayable(html)
        if (direct != null) return direct
        val atobRegex = Pattern.compile("atob\\(['\"]([A-Za-z0-9+/=]+)['\"]\\)")
        val m = atobRegex.matcher(html)
        while (m.find()) {
            val b64 = m.group(1)
            try {
                val decoded = String(Base64.decode(b64, Base64.DEFAULT))
                val p = findFirstPlayable(decoded)
                if (p != null) return p
            } catch (_: Exception) {
            }
        }
        val contentRegex = Pattern.compile("\"content\"\\s*:\\s*\"(https?:[^\"']+?\\.(?:m3u8|mp4)[^\"]*)\"")
        val mc = contentRegex.matcher(html)
        if (mc.find()) return mc.group(1).replace("\\/".toRegex(), "/")
        return null
    }

    private fun resolveFembed(url: String): String? {
        val html = downloadHtml(url) ?: return null
        val direct = findFirstPlayable(html)
        if (direct != null) return direct
        val sourcesRegex = Pattern.compile("\"file\"\\s*:\\s*\"(https?:[^\"']+?\\.(?:m3u8|mp4)[^\"]*)\"")
        val ms = sourcesRegex.matcher(html)
        if (ms.find()) return ms.group(1).replace("\\/".toRegex(), "/")
        return null
    }

    private fun resolveOkRu(url: String): String? {
        val html = downloadHtml(url) ?: return null
        val direct = findFirstPlayable(html)
        if (direct != null) return direct
        val hlsRegex = Pattern.compile("\"hlsManifestUrl\"\\s*:\\s*\"(https?:[^\"']+?\\.m3u8[^\"]*)\"")
        val mh = hlsRegex.matcher(html)
        if (mh.find()) return mh.group(1).replace("\\/".toRegex(), "/")
        val m3u8Regex = Pattern.compile("(https?://[^\"']+?\\.m3u8[^\"']*)")
        val m3 = m3u8Regex.matcher(html)
        if (m3.find()) return m3.group(1)
        return null
    }

    private fun findFirstPlayable(text: String): String? {
        val r = Pattern.compile("(https?://[^\"'\\s]+?\\.(?:m3u8|mp4)[^\"'\\s]*)")
        val m = r.matcher(text)
        return if (m.find()) m.group(1) else null
    }

    private fun resolveRecursive(url: String, visited: MutableSet<String>, depth: Int, referer: String?): String? {
        if (depth > 2 || visited.contains(url)) return null
        visited.add(url)
        return try {
            val html = downloadHtml(url, referer) ?: return null
            val doc = Jsoup.parse(html, url)
            val sources = doc.select("video source[src], source[src]")
            for (s in sources) {
                val src = s.attr("abs:src")
                if (isPlayable(src)) return src
            }
            val scripts = doc.select("script")
            val pattern = Pattern.compile("(https?:\\\\?/\\\\?/[^\\s\"']+(?:m3u8|mp4))")
            for (s in scripts) {
                val m = pattern.matcher(s.data())
                if (m.find()) {
                    val raw = m.group(1)
                    val cleaned = raw.replace("\\/\\/".toRegex(), "//").replace("\\/".toRegex(), "/")
                    if (isPlayable(cleaned)) return cleaned
                }
            }
            // Buscar enlaces de hosts comunes aunque no terminen en .m3u8/.mp4
            val hostUrlRegex = Pattern.compile("(https?://[^\\s\"']*(voe|streamwish|wish|fembed|ok\\.ru)[^\\s\"']*)", Pattern.CASE_INSENSITIVE)
            for (s in scripts) {
                val mh = hostUrlRegex.matcher(s.data())
                if (mh.find()) {
                    val link = mh.group(1).replace("\\/".toRegex(), "/")
                    val byHost = resolveByHost(link)
                    if (byHost != null) return byHost
                }
            }
            val ifr = doc.select("iframe[src]")
            for (i in ifr) {
                val src = i.attr("abs:src")
                if (isPlayable(src)) return src
                val deeper = resolveRecursive(src, visited, depth + 1, url)
                if (deeper != null) return deeper
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun originFrom(url: String?): String? {
        return try {
            if (url.isNullOrEmpty()) return null
            val u = URI(url)
            if (u.scheme == null || u.host == null) null else "${u.scheme}://${u.host}"
        } catch (_: Exception) {
            null
        }
    }
}
