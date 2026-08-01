package app.cuevanatv.net

import app.cuevanatv.Config
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class ApiClient {
    companion object {
        private const val CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private var sharedClient: OkHttpClient? = null

        fun getClient(): OkHttpClient {
            if (sharedClient == null) {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("SSL").apply { init(null, trustAllCerts, java.security.SecureRandom()) }
                sharedClient = OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("User-Agent", CHROME_UA).build())
                    }.build()
            }
            return sharedClient!!
        }
    }

    private val client = getClient()
    private fun restUrl() = "${Config.SUPABASE_URL}/rest/v1"
    private fun newReq(url: String) = Request.Builder().url(url)
        .addHeader("apikey", Config.SUPABASE_ANON_KEY)
        .addHeader("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")

    suspend fun getFeed(category: String?): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        try {
            val baseUrl = "${restUrl()}/titles?published=eq.true&select=id,title,poster_url,category,type,is_live,playable_url,source_page_url"
            val filter = if (!category.isNullOrEmpty() && category != "Todas") {
                "&category=ilike.*${URLEncoder.encode(category.replace("í","i").replace("á","a"), "UTF-8")}*"
            } else ""
            
            val finalUrl = "$baseUrl$filter&order=created_at.desc"
            println("[SUPABASE] URL Feed Generada: $finalUrl")

            client.newCall(newReq(finalUrl).build()).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                val arr = JSONArray(bodyString)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    result.add(VideoItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        imageUrl = o.optString("poster_url"),
                        playableUrl = o.optString("playable_url").takeIf { it != "null" && it.isNotBlank() },
                        sourcePageUrl = o.optString("source_page_url").takeIf { it != "null" && it.isNotBlank() },
                        category = o.optString("category"),
                        type = o.optString("type", "movie"),
                        isLive = o.optBoolean("is_live", false)
                    ))
                }
            }
        } catch (e: Exception) { println("[API] Error Feed: ${e.message}") }
        result
    }

    suspend fun getServersForTitle(titleId: String): List<ServerItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ServerItem>()
        try {
            val url = "${restUrl()}/servers?title_id=eq.$titleId&select=*&order=season_number.asc,episode_number.asc"
            println("[SUPABASE] Consultando Episodios: $url")
            
            client.newCall(newReq(url).build()).execute().use { resp ->
                val arr = JSONArray(resp.body?.string() ?: "[]")
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    list.add(ServerItem(
                        id = s.optString("id"),
                        titleId = s.optString("title_id"),
                        name = s.optString("name"),
                        playable_url = s.optString("playable_url").takeIf { it != "null" && it.isNotBlank() },
                        page_url = s.optString("page_url").takeIf { it != "null" && it.isNotBlank() },
                        season_number = if (s.isNull("season_number")) null else s.optInt("season_number"),
                        episode_number = if (s.isNull("episode_number")) null else s.optInt("episode_number")
                    ))
                }
            }
        } catch (e: Exception) { println("[API] Error Episodios: ${e.message}") }
        list
    }

    suspend fun getDetails(id: String): VideoItem? = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/titles?id=eq.$id&select=*"
            client.newCall(newReq(url).build()).execute().use { resp ->
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() == 0) return@withContext null
                val o = arr.getJSONObject(0)
                return@withContext VideoItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    imageUrl = o.optString("poster_url"),
                    playableUrl = o.optString("playable_url").takeIf { it != "null" && it.isNotBlank() },
                    sourcePageUrl = o.optString("source_page_url").takeIf { it != "null" && it.isNotBlank() },
                    type = o.optString("type"),
                    description = o.optString("description")
                )
            }
        } catch (e: Throwable) { null }
    }

    suspend fun login(email: String, password: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")
            
            val fetchUrl = "${restUrl()}/app_users?select=id,email,active,days_remaining,fecha_vencimiento,limite_pantallas,bypass_qr&email=eq.$encodedEmail&password=eq.$encodedPassword"
            println("[LOGIN] Intentando acceso: $fetchUrl")
            
            val request = newReq(fetchUrl).addHeader("Cache-Control", "no-cache").get().build()
            client.newCall(request).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) {
                    println("[LOGIN] Error HTTP [${resp.code}]: $bodyString")
                    return@withContext null
                }
                
                val arr = JSONArray(bodyString)
                if (arr.length() == 0) {
                    println("[LOGIN] Usuario o contraseña incorrectos.")
                    return@withContext JSONObject()
                }
                
                val user = arr.getJSONObject(0)
                println("[LOGIN] Acceso concedido para: ${user.optString("email")}")
                return@withContext user
            }
        } catch (e: Exception) {
            println("[LOGIN] Excepción: ${e.message}")
            null
        }
    }

    suspend fun register(email: String, pass: String, whatsapp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", pass)
                .put("whatsapp", whatsapp)
                .put("active", true)
                .toString().toRequestBody("application/json".toMediaType())
            
            val request = newReq("${restUrl()}/app_users").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }
}
