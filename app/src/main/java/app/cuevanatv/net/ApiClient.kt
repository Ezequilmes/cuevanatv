package app.cuevanatv.net

import android.content.Context
import app.cuevanatv.BuildConfig
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ApiClient(private val context: Context) {
    private val client = OkHttpClient()

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL
    private fun anonKey(): String = BuildConfig.SUPABASE_ANON_KEY
    private fun restUrl(): String = "${baseUrl().trimEnd('/')}/rest/v1"
    private fun authUrl(): String = "${baseUrl().trimEnd('/')}/auth/v1"

    suspend fun login(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext null
        
        // Consultamos la tabla 'app_users' directamente via REST API
        val url = "$restUrl/app_users?email=eq.$email&password=eq.$password&select=email,active"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .get()
            .build()
            
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val arr = JSONArray(body)
            if (arr.length() > 0) {
                val user = arr.getJSONObject(0)
                if (user.optBoolean("active", false)) {
                    // Retornamos el email como "token" para identificar la sesión, 
                    // ya que la tabla app_users no usa Supabase Auth (JWT)
                    return@withContext email
                }
            }
            null
        }
    }

    suspend fun getFeed(token: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext result
        // Usamos poster_url que es el nombre real en la tabla titles
        val url =
            "$restUrl/titles?select=id,title,poster_url&published=eq.true&order=created_at.desc.nullslast"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext result
            val arr = JSONArray(resp.body?.string() ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val title = o.optString("title")
                val poster = o.optString("poster_url")
                val type = o.optString("type", "movie")
                val category = o.optString("category", "")
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    result.add(
                        VideoItem(
                            title = title,
                            imageUrl = poster,
                            streamUrl = "api://title/$id",
                            type = type,
                            category = category
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
        // Usamos los nombres reales de la tabla: playable_url en lugar de playableUrl
        val url =
            "$restUrl/titles?id=eq.$id&select=id,title,description,servers(name,playable_url,priority)"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
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
                val playable = s.optString("playable_url", "")
                val urlUse = if (playable.isNotEmpty()) playable else null
                servers.add(ServerItem(name, urlUse, null))
            }
            MovieDetails(description, servers)
        }
    }
}
