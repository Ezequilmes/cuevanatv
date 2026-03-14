package app.cuevanatv.net

import android.content.Context
import app.cuevanatv.BuildConfig
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

class ApiClient(private val context: Context) {
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL
    private fun anonKey(): String = BuildConfig.SUPABASE_ANON_KEY
    private fun restUrl(): String = "${baseUrl().trimEnd('/')}/rest/v1"
    private fun authUrl(): String = "${baseUrl().trimEnd('/')}/auth/v1"

    suspend fun login(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext null
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(json)
        val req = Request.Builder()
            .url("$authUrl/token?grant_type=password")
            .addHeader("apikey", anonKey())
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val obj = JSONObject(resp.body?.string() ?: return@withContext null)
            obj.optString("access_token", null)
        }
    }

    suspend fun getFeed(token: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext result
        val url =
            "$restUrl/titles?select=id,title,posterUrl&published=eq.true&order=createdAt.desc.nullslast"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val arr = JSONArray(resp.body?.string() ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val title = o.optString("title")
                val poster = o.optString("posterUrl")
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    result.add(
                        VideoItem(
                            title = title,
                            imageUrl = poster,
                            streamUrl = "api://title/$id"
                        )
                    )
                }
            }
        }
        result
    }

    suspend fun getDetails(token: String, id: String): MovieDetails = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) {
            return@withContext MovieDetails("No disponible", emptyList())
        }
        val url =
            "$restUrl/titles?id=eq.$id&select=id,title,description,servers(name,playableUrl,pageUrl,priority)"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext MovieDetails("No disponible", emptyList())
            val arr = JSONArray(resp.body?.string() ?: "[]")
            if (arr.length() == 0) return@withContext MovieDetails("No disponible", emptyList())
            val obj = arr.getJSONObject(0)
            val description = obj.optString("description", "Sin descripción")
            val servers = mutableListOf<ServerItem>()
            val sArr = obj.optJSONArray("servers") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val s = sArr.getJSONObject(i)
                val name = s.optString("name", "Servidor")
                val playable = s.optString("playableUrl", "")
                val page = s.optString("pageUrl", "")
                val urlUse = if (playable.isNotEmpty()) playable else if (page.isNotEmpty()) page else null
                servers.add(ServerItem(name, urlUse, null))
            }
            MovieDetails(description, servers)
        }
    }
}
