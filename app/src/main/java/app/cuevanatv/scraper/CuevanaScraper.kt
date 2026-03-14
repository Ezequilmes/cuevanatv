package app.cuevanatv.scraper

import app.cuevanatv.model.VideoItem
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

class CuevanaScraper {
    private val baseUrl = "https://www2.gnula.one/."

    suspend fun fetchLatestMovies(): List<VideoItem> = withContext(Dispatchers.IO) {
        val movies = mutableListOf<VideoItem>()
        try {
            val doc: Document = Jsoup.connect(baseUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", baseUrl)
                .referrer(baseUrl)
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(10000)
                .get()
            val candidates: List<Elements> = listOf(
                doc.select(".items article, .post-video")
            )
            fun extract(el: org.jsoup.nodes.Element) {
                val a = el.selectFirst("a[href]") ?: el
                val link = a.attr("abs:href")
                val imgEl = el.selectFirst("img") ?: a.selectFirst("img")
                val posterUrl = when {
                    imgEl == null -> ""
                    imgEl.hasAttr("abs:data-src") -> imgEl.attr("abs:data-src")
                    imgEl.hasAttr("data-src") -> imgEl.attr("abs:data-src")
                    else -> imgEl.attr("abs:src")
                }
                val title = el.select("h3, .title, img[alt]").firstOrNull()?.let {
                    if (it.tagName() == "img") it.attr("alt") else it.text()
                } ?: a.attr("title").ifEmpty { a.text() }
                if (title.isNotEmpty() && link.isNotEmpty()) {
                    movies.add(VideoItem(title = title, imageUrl = posterUrl, streamUrl = link))
                }
            }
            for (group in candidates) {
                group.forEach { extract(it) }
                if (movies.isNotEmpty()) break
            }
        } catch (_: Exception) {
        }
        movies
    }

    suspend fun fetchMovieDetails(movieUrl: String): MovieDetails = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(movieUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", baseUrl)
                .referrer(baseUrl)
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(10000)
                .get()
            val description = doc.select(".resumen, #sinopsis, .sinopsis, .description, #description, .plot, .movie-description").text()
            val servers = mutableListOf<ServerItem>()
            val serverElements = doc.select(
                ".player_options button, .player_options li, .player_options a, " +
                        ".opciones_nav button, .opciones_nav li, .opciones_nav a, " +
                        "[data-url], [data-href], [data-link], [data-src], [data-player], [data-embed]"
            )
            val seen = HashSet<String>()
            for (el in serverElements) {
                val name = el.text().trim()
                var serverUrl = el.absUrl("data-url")
                if (serverUrl.isEmpty()) serverUrl = el.absUrl("data-href")
                if (serverUrl.isEmpty()) serverUrl = el.absUrl("data-link")
                if (serverUrl.isEmpty()) serverUrl = el.absUrl("data-src")
                if (serverUrl.isEmpty()) serverUrl = el.absUrl("href")
                if (serverUrl.isEmpty()) serverUrl = el.attr("data-url")
                if (serverUrl.isEmpty()) serverUrl = el.attr("data-href")
                if (serverUrl.isEmpty()) serverUrl = el.attr("data-link")
                if (serverUrl.isEmpty()) serverUrl = el.attr("data-src")
                if (serverUrl.isEmpty()) serverUrl = el.attr("href")
                if (serverUrl.startsWith("//")) serverUrl = "https:$serverUrl"
                if (serverUrl.isEmpty()) {
                    val pid = el.attr("data-player")
                    if (pid.isNotEmpty()) {
                        val target = doc.select("#$pid iframe[src], #$pid a[href]")
                        val abs = target.firstOrNull()?.let { it.absUrl("src").ifEmpty { it.absUrl("href") } } ?: ""
                        if (abs.isNotEmpty()) serverUrl = abs
                    }
                }
                if (name.isNotEmpty() && serverUrl.isNotEmpty()) {
                    if (seen.add(serverUrl)) {
                        servers.add(ServerItem(name, serverUrl, null))
                    }
                }
            }
            if (servers.isEmpty()) {
                val iframes = doc.select("iframe[src]")
                for (i in iframes) {
                    val src = i.absUrl("src").ifEmpty { i.attr("src") }
                    val url = if (src.startsWith("//")) "https:$src" else src
                    if (url.startsWith("http") && seen.add(url)) {
                        servers.add(ServerItem("Iframe", url, null))
                    }
                }
            }
            if (servers.isEmpty()) {
                val scripts = doc.select("script")
                val rx = Regex("https?://[^\"'\\s]+", RegexOption.IGNORE_CASE)
                for (s in scripts) {
                    val d = s.data()
                    rx.findAll(d).forEach { m ->
                        val url = m.value
                        val add = (
                            url.contains("voe", true) ||
                            url.contains("fembed", true) ||
                            url.contains("streamwish", true) ||
                            url.contains("wish", true) ||
                            url.contains("ok.ru", true) ||
                            url.endsWith(".m3u8", true) ||
                            url.endsWith(".mp4", true)
                        )
                        if (add) {
                            val cleaned = if (url.startsWith("//")) "https:$url" else url
                            if (seen.add(cleaned)) {
                                servers.add(ServerItem("Servidor", url, null))
                            }
                        }
                    }
                    if (servers.isNotEmpty()) break
                }
            }
            MovieDetails(if (description.isEmpty()) "Sin descripción" else description, servers)
        } catch (_: Exception) {
            MovieDetails("No se pudo cargar la descripción", emptyList())
        }
    }
}
