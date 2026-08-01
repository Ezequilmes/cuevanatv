package app.cuevanatv.net

import android.content.Context
import app.cuevanatv.BuildConfig
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiClient(private val context: Context) {
    private val client = OkHttpClient()

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        
        fun getUnsafeClient(): OkHttpClient {
            try {
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                return OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()
            } catch (e: Exception) {
                return OkHttpClient()
            }
        }
    }

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL
    private fun anonKey(): String = BuildConfig.SUPABASE_ANON_KEY
    fun restUrl(): String = "${baseUrl().trimEnd('/')}/rest/v1"
    fun authUrl(): String = "${baseUrl().trimEnd('/')}/auth/v1"

    suspend fun createMercadoPagoPreference(email: String): String? = withContext(Dispatchers.IO) {
        // Implementación de referencia para GOLD
        null 
    }

    suspend fun checkStatus(email: String): JSONObject? = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext null
        val url = "${restUrl()}/app_users?email=eq.$email&select=*"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() > 0) arr.getJSONObject(0) else null
            }
        } catch (e: Exception) { null }
    }

    suspend fun register(email: String, pass: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext false
        val payload = JSONObject().apply {
            put("email", email)
            put("password", pass)
            put("whatsapp", phone)
            put("active", true)
            put("limite_pantallas", 1)
        }
        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("${restUrl()}/app_users")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .addHeader("Prefer", "return=representation")
            .post(body)
            .build()
        try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun getUserDevices(userId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext emptyList<JSONObject>()
        val url = "${restUrl()}/user_devices?user_id=eq.$userId&select=*"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList<JSONObject>()
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                list
            }
        } catch (e: Exception) { emptyList<JSONObject>() }
    }

    suspend fun registerDevice(userId: String, deviceId: String, model: String) = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext
        val payload = JSONObject().apply {
            put("user_id", userId)
            put("device_id", deviceId)
            put("model", model)
        }
        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("${restUrl()}/user_devices")
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .post(body)
            .build()
        try { client.newCall(req).execute().close() } catch (e: Exception) {}
    }

    suspend fun login(email: String, password: String): JSONObject? = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext null
        val url = "${restUrl()}/app_users?email=eq.$email&password=eq.$password&select=*"
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Authorization", "Bearer ${anonKey()}")
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() > 0) arr.getJSONObject(0) else null
            }
        } catch (e: Exception) { null }
    }

    suspend fun loginToken(email: String, password: String): String? = withContext(Dispatchers.IO) {
        if (baseUrl().isEmpty() || anonKey().isEmpty()) return@withContext null
        
        // Consultamos la tabla 'app_users' directamente via REST API
        val url = "${restUrl()}/app_users?email=eq.$email&password=eq.$password&select=email,active"
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
        // CORRECCIÓN: Solicitamos type e is_live para que la APK sepa si es Película o Canal
        val url =
            "${restUrl()}/titles?select=id,title,poster_url,type,is_live,category&published=eq.true&order=created_at.desc.nullslast"
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
                val isLive = o.optBoolean("is_live", false)
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    result.add(
                        VideoItem(
                            id = id,
                            title = title,
                            imageUrl = poster,
                            streamUrl = "api://title/$id",
                            type = type,
                            category = category,
                            isLive = isLive
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
        // FIX: Especificamos la relación exacta servers!servers_titleId_fkey para evitar error de múltiples llaves foráneas
        val url =
            "${restUrl()}/titles?id=eq.$id&select=id,title,description,servers!servers_titleId_fkey(name,playable_url,priority,episode_number,season_number)"
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
                val epNum = if (s.has("episode_number") && !s.isNull("episode_number")) s.getInt("episode_number") else null
                val urlUse = if (playable.isNotEmpty()) playable else null
                servers.add(ServerItem(name, urlUse, null, epNum))
            }
            MovieDetails(description, servers)
        }
    }
}
