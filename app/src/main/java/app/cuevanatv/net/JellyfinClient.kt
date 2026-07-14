package app.cuevanatv.net

import android.content.Context
import android.provider.Settings
import app.cuevanatv.Auth
import app.cuevanatv.BuildConfig
import app.cuevanatv.JellyfinPrefs
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class JellyfinClient(private val context: Context) {
    private val client = ApiClient.getUnsafeClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = baseUrl().isNotBlank() && apiKey().isNotBlank()

    suspend fun getViews(limit: Int = 50): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val url = absUrl("/Users/$userId/Views")
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val obj = JSONObject(resp.body?.string().orEmpty())
            val items = obj.optJSONArray("Items") ?: JSONArray()
            val count = minOf(items.length(), limit)
            for (i in 0 until count) {
                val o = items.optJSONObject(i) ?: continue
                val id = o.optString("Id").trim()
                val name = o.optString("Name").trim()
                if (id.isBlank() || name.isBlank()) continue
                val poster = absUrl("/Items/$id/Images/Primary?maxWidth=300&quality=90")
                result.add(VideoItem(title = name, imageUrl = poster, streamUrl = "jellyfin://browse/$id"))
            }
        }
        result
    }

    suspend fun getResume(limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val url = absUrl("/Users/$userId/Items/Resume?Limit=$limit&Fields=PrimaryImageAspectRatio")
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val obj = JSONObject(resp.body?.string().orEmpty())
            val items = obj.optJSONArray("Items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val parsed = parseItem(o) ?: continue
                if (parsed.streamUrl?.startsWith("jellyfin://item/") == true) {
                    result.add(parsed)
                }
            }
        }
        result
    }

    suspend fun getLatestMovies(limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val url = absUrl(
            "/Users/$userId/Items/Latest?IncludeItemTypes=Movie&Limit=$limit&Fields=Overview,ProductionYear,PrimaryImageAspectRatio"
        )
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val arr = JSONArray(resp.body?.string() ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val parsed = parseItem(o) ?: continue
                if (parsed.streamUrl?.startsWith("jellyfin://item/") == true) {
                    result.add(parsed)
                }
            }
        }
        result
    }

    suspend fun getLatestEpisodes(limit: Int = 30): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val url = absUrl(
            "/Users/$userId/Items/Latest?IncludeItemTypes=Episode&Limit=$limit&Fields=Overview,ProductionYear,PrimaryImageAspectRatio"
        )
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val arr = JSONArray(resp.body?.string() ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val parsed = parseItem(o) ?: continue
                if (parsed.streamUrl?.startsWith("jellyfin://item/") == true) {
                    result.add(parsed)
                }
            }
        }
        result
    }

    suspend fun browse(parentId: String, limit: Int = 60): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val url = absUrl(
            "/Users/$userId/Items?ParentId=$parentId&SortBy=SortName&SortOrder=Ascending&Recursive=false&Limit=$limit&Fields=PrimaryImageAspectRatio"
        )
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val obj = JSONObject(resp.body?.string().orEmpty())
            val items = obj.optJSONArray("Items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val parsed = parseItem(o) ?: continue
                result.add(parsed)
            }
        }
        result
    }

    suspend fun search(term: String, limit: Int = 60): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val userId = getOrFetchUserId() ?: return@withContext result
        val q = java.net.URLEncoder.encode(term, "UTF-8")
        val url = absUrl(
            "/Users/$userId/Items?SearchTerm=$q&Recursive=true&IncludeItemTypes=Movie,Series,Season,Episode&Limit=$limit&Fields=PrimaryImageAspectRatio"
        )
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val obj = JSONObject(resp.body?.string().orEmpty())
            val items = obj.optJSONArray("Items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val parsed = parseItem(o) ?: continue
                result.add(parsed)
            }
        }
        result
    }

    suspend fun getDetails(itemId: String): MovieDetails = withContext(Dispatchers.IO) {
        val userId = getOrFetchUserId() ?: return@withContext MovieDetails("No disponible", emptyList())
        val overview = fetchOverview(userId, itemId) ?: "Sin descripción"
        val playable = fetchPlaybackUrl(userId, itemId)
        val servers =
            if (!playable.isNullOrBlank()) listOf(ServerItem("Reproducir", playable, null, null))
            else emptyList()
        MovieDetails(overview, servers)
    }

    fun token(): String = apiKey()

    fun serverUrl(): String = baseUrl()

    fun isFromServer(url: String): Boolean {
        val base = baseUrl().trim().trimEnd('/')
        if (base.isBlank()) return false
        return url.trim().startsWith(base, ignoreCase = true)
    }

    private fun baseUrl(): String {
        val runtime = JellyfinPrefs.getUrl(context)
        if (runtime.isNotBlank()) return runtime
        return BuildConfig.JELLYFIN_URL.trim()
    }

    private fun apiKey(): String {
        val runtime = JellyfinPrefs.getApiKey(context)
        if (runtime.isNotBlank()) return runtime
        return BuildConfig.JELLYFIN_API_KEY.trim()
    }

    private fun configuredUserId(): String = BuildConfig.JELLYFIN_USER_ID.trim()

    private fun absUrl(pathOrUrl: String): String {
        val p = pathOrUrl.trim()
        if (p.startsWith("http://", true) || p.startsWith("https://", true)) return p
        val base = baseUrl().trimEnd('/')
        val path = if (p.startsWith("/")) p else "/$p"
        return base + path
    }

    private fun requestBuilder(url: String): Request.Builder {
        val token = apiKey()
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("X-Emby-Token", token)
            .addHeader("X-Emby-Authorization", embyAuthorizationHeader())
    }

    private fun embyAuthorizationHeader(): String {
        val deviceId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.isNotBlank() }
                ?: "unknown"
        return "MediaBrowser Client=\"CuevanaTV\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\""
    }

    private suspend fun getOrFetchUserId(): String? = withContext(Dispatchers.IO) {
        val explicit = configuredUserId()
        if (explicit.isNotBlank()) return@withContext explicit
        val cached = Auth.getJellyfinUserId(context)?.trim().orEmpty()
        if (cached.isNotBlank()) return@withContext cached
        if (!isConfigured()) return@withContext null
        val url = absUrl("/Users")
        val req = requestBuilder(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val arr = JSONArray(resp.body?.string() ?: "[]")
            val first = arr.optJSONObject(0) ?: return@withContext null
            val id = first.optString("Id").trim()
            if (id.isBlank()) return@withContext null
            Auth.saveJellyfinUserId(context, id)
            id
        }
    }

    private fun fetchOverview(userId: String, itemId: String): String? {
        val candidates = listOf(
            absUrl("/Users/$userId/Items/$itemId?Fields=Overview"),
            absUrl("/Items/$itemId?Fields=Overview")
        )
        for (url in candidates) {
            val req = requestBuilder(url).get().build()
            val ov = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JSONObject(resp.body?.string().orEmpty())
                obj.optString("Overview").trim().takeIf { it.isNotBlank() }
            }
            if (!ov.isNullOrBlank()) return ov
        }
        return null
    }

    private fun fetchPlaybackUrl(userId: String, itemId: String): String? {
        val url = absUrl("/Items/$itemId/PlaybackInfo?UserId=$userId")
        val body = "{}".toRequestBody(json)
        val req = requestBuilder(url).post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val obj = JSONObject(resp.body?.string().orEmpty())
            val sources = obj.optJSONArray("MediaSources") ?: JSONArray()
            val first = sources.optJSONObject(0) ?: return null
            val transcoding = first.optString("TranscodingUrl").trim()
            if (transcoding.isNotBlank()) return absUrl(transcoding)
            val direct = first.optString("DirectStreamUrl").trim()
            if (direct.isNotBlank()) return absUrl(direct)
            val fallback = absUrl("/Videos/$itemId/stream?static=true")
            return fallback
        }
    }

    private fun parseItem(o: JSONObject): VideoItem? {
        val id = o.optString("Id").trim()
        val name = o.optString("Name").trim()
        if (id.isBlank() || name.isBlank()) return null
        val type = o.optString("Type").trim()
        val poster = absUrl("/Items/$id/Images/Primary?maxWidth=300&quality=90")
        val streamUrl =
            when (type) {
                "Movie", "Episode", "Video" -> "jellyfin://item/$id"
                else -> "jellyfin://browse/$id"
            }
        return VideoItem(title = name, imageUrl = poster, streamUrl = streamUrl)
    }
}
