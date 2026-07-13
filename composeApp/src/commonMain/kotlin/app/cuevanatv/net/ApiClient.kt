package app.cuevanatv.net

import app.cuevanatv.Auth
import app.cuevanatv.Config
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class ApiClient {
    private val TAG = "ApiClient"
    private val TAG_NET = "ApiClient_NET"
    private val TAG_MP = "ApiClient_MP"

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private var sharedUnsafeClient: OkHttpClient? = null

        fun getUnsafeClient(followRedirects: Boolean = false): OkHttpClient {
            if (sharedUnsafeClient == null) {
                sharedUnsafeClient = createUnsafeOkHttpClient(followRedirects)
            }
            return sharedUnsafeClient!!
        }

        private fun createUnsafeOkHttpClient(followRedirects: Boolean): OkHttpClient {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                return OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .followRedirects(followRedirects)
                    .followSslRedirects(followRedirects)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val request = original.newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .method(original.method, original.body)
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val unsafeClient: OkHttpClient
        get() = getUnsafeClient()

    private fun baseUrl(): String = Config.SUPABASE_URL
    private fun anonKey(): String = Config.SUPABASE_ANON_KEY
    private fun restUrl(): String = "${baseUrl()}/rest/v1"

    private fun newRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Content-Type", "application/json")

        if (!url.contains("/functions/v1/")) {
            builder.addHeader("Authorization", "Bearer ${anonKey()}")
        }

        return builder
    }

    suspend fun login(email: String, password: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")
            
            val fetchUrl = "${restUrl()}/app_users?select=id,email,active,days_remaining,fecha_vencimiento,limite_pantallas,bypass_qr&email=eq.$encodedEmail&password=eq.$encodedPassword"
            println("Login Attempt URL: $fetchUrl")
            
            val req = newRequest(fetchUrl).addHeader("Cache-Control", "no-cache").get().build()
            unsafeClient.newCall(req).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) {
                    println("Error en Login [${resp.code}]: $bodyString")
                    return@withContext if (resp.code == 401 || resp.code == 403) {
                        JSONObject().put("error_type", "auth").put("code", resp.code)
                    } else null
                }
                
                val arr = JSONArray(bodyString)
                if (arr.length() == 0) return@withContext JSONObject()
                
                val user = arr.getJSONObject(0)
                if (user.optBoolean("bypass_qr", false)) {
                    user.put("active", true)
                    user.put("days_remaining", 999)
                }
                return@withContext user
            }
        } catch (e: Exception) {
            println("Excepción en Login: ${e.message}")
            null
        }
    }

    suspend fun getUserDevices(userId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val list = mutableListOf<JSONObject>()
        try {
            val url = "${restUrl()}/user_devices?user_id=eq.$userId&select=device_id"
            val req = newRequest(url).get().build()
            unsafeClient.newCall(req).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) {
                    println("Error en GET_DEVICES [${resp.code}]: $bodyString")
                    return@withContext list
                }
                val arr = JSONArray(bodyString)
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            println("Excepción en GET_DEVICES: ${e.message}")
        }
        list
    }

    suspend fun registerDevice(userId: String, deviceId: String, deviceModel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/user_devices"
            val body = JSONObject().put("user_id", userId).put("device_id", deviceId).put("device_model", deviceModel).toString().toRequestBody(jsonMediaType)
            val req = newRequest(url).post(body).build()
            unsafeClient.newCall(req).execute().use { it.isSuccessful || it.code == 409 }
        } catch (e: Exception) {
            println("Excepción en REG_DEVICE: ${e.message}")
            true 
        }
    }

    suspend fun register(email: String, pass: String, whatsapp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/app_users"
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }
            val trialExpiry = sdf.format(calendar.time)

            val body = JSONObject()
                .put("email", email.trim())
                .put("password", pass)
                .put("whatsapp", whatsapp)
                .put("active", true) 
                .put("days_remaining", 3)
                .put("fecha_vencimiento", trialExpiry)
                .put("limite_pantallas", 1)
                .toString().toRequestBody(jsonMediaType)

            val req = newRequest(url).post(body).build()
            unsafeClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            println("Excepción en REGISTER: ${e.message}")
            false
        }
    }

    suspend fun checkStatus(id: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val pingUrl = "${restUrl()}/app_users?id=eq.$id"
            val pingBody = JSONObject().put("ultima_conexion", sdf.format(Date())).toString().toRequestBody(jsonMediaType)
            unsafeClient.newCall(newRequest(pingUrl).patch(pingBody).build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {}
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
            })

            val url = "${restUrl()}/app_users?id=eq.$id&select=active,days_remaining,bypass_qr,fecha_vencimiento,limite_pantallas"
            unsafeClient.newCall(newRequest(url).get().build()).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) return@withContext passiveState()
                val arr = JSONArray(bodyString)
                if (arr.length() > 0) {
                    val user = arr.getJSONObject(0)
                    if (user.optBoolean("bypass_qr", false)) {
                        user.put("active", true)
                        user.put("days_remaining", 999)
                    }
                    return@withContext user
                }
            }
        } catch (e: Exception) {
            println("Error en checkStatus: ${e.message}")
        }
        passiveState()
    }

    private fun passiveState() = JSONObject().put("active", true).put("days_remaining", 3).put("bypass_qr", false)

    suspend fun createMercadoPagoPreference(emailUsuario: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/functions/v1/mercadopago-webhook"
            val trimmedEmail = emailUsuario.trim()
            val body = JSONObject().put("email", trimmedEmail).toString().toRequestBody(jsonMediaType)
            
            var tokenToSend = Auth.getToken()
            if (tokenToSend.isNullOrEmpty()) {
                tokenToSend = anonKey()
            }

            val builder = newRequest(url).post(body)
            builder.addHeader("Authorization", "Bearer $tokenToSend")
            
            val request = builder.build()

            println("Lanzando Checkout para: $trimmedEmail")

            unsafeClient.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val initPoint = JSONObject(responseStr).optString("init_point")
                    println("Exito: $initPoint")
                    return@withContext initPoint
                } else {
                    println("Fallo Checkout [Code: ${response.code}]: $responseStr")
                }
            }
        } catch (e: Exception) {
            println("Excepción crítica en Checkout: ${e.message}")
        }
        null
    }

    suspend fun getFeed(token: String?): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        try {
            val url = "${restUrl()}/titles?published=eq.true&select=id,title,description,poster_url,source_page_url,category,type,is_live,playable_url&order=created_at.desc"
            unsafeClient.newCall(newRequest(url).get().build()).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) {
                    println("Error HTTP Feed [${resp.code}]: $bodyString")
                    return@withContext result
                }
                val arr = JSONArray(bodyString)
                for (i in 0 until arr.length()) {
                    try {
                        val o = arr.getJSONObject(i)
                        val isLive = o.optBoolean("is_live", false)
                        val playableUrl = if (!o.isNull("playable_url")) o.optString("playable_url", "").trim() else ""
                        if (isLive && (playableUrl.isEmpty() || playableUrl == "null")) continue
                        
                        val posterUrl = if (!o.isNull("poster_url")) o.optString("poster_url") else null
                        
                        result.add(VideoItem(
                            id = if (!o.isNull("id")) o.optString("id") else null,
                            title = if (!o.isNull("title")) o.optString("title") else null,
                            imageUrl = posterUrl,
                            streamUrl = "api://title/${o.optString("id", "")}",
                            playableUrl = if (playableUrl.isNotEmpty()) playableUrl else null,
                            sourcePageUrl = if (!o.isNull("source_page_url")) o.optString("source_page_url") else null,
                            category = if (!o.isNull("category")) o.optString("category") else null,
                            type = if (!o.isNull("type")) o.optString("type", "movie") else "movie",
                            isLive = isLive,
                            description = if (!o.isNull("description")) o.optString("description") else null
                        ))
                    } catch (e: Exception) {
                        println("Error parseando item feed index $i: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error en getFeed: ${e.message}")
        }
        result
    }

    suspend fun getDetails(token: String?, id: String): MovieDetails = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/titles?id=eq.$id&select=description,poster_url,source_page_url,type,playable_url,servers(id,name,playable_url,referer,fallback_magnet,priority,season_number,episode_number)&servers.order=priority.desc"
            unsafeClient.newCall(newRequest(url).get().build()).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) return@withContext MovieDetails()
                
                val arr = JSONArray(bodyString)
                if (arr.length() == 0) return@withContext MovieDetails()
                
                val o = arr.getJSONObject(0)
                val serversArr = o.optJSONArray("servers") ?: JSONArray()
                val serverList = mutableListOf<ServerItem>()
                for (i in 0 until serversArr.length()) {
                    val s = serversArr.getJSONObject(i)
                    var pUrl = s.optString("playable_url", "")
                    if (pUrl == "null" || pUrl.isBlank()) pUrl = ""
                    
                    serverList.add(ServerItem(
                        id = s.optString("id"),
                        name = s.optString("name"),
                        playable_url = if (pUrl.isNotEmpty()) pUrl else null,
                        referer = s.optString("referer", null),
                        fallbackMagnet = s.optString("fallback_magnet", null),
                        priority = s.optInt("priority", 0),
                        season_number = if (!s.isNull("season_number")) s.optInt("season_number") else null,
                        episode_number = if (!s.isNull("episode_number")) s.optInt("episode_number") else null
                    ))
                }
                
                return@withContext MovieDetails(
                    description = o.optString("description"),
                    servers = serverList,
                    type = o.optString("type", "movie"),
                    playableUrl = if (!o.isNull("playable_url")) o.optString("playable_url") else null,
                    sourcePageUrl = if (!o.isNull("source_page_url")) o.optString("source_page_url") else null,
                    posterUrl = if (!o.isNull("poster_url")) o.optString("poster_url") else null
                )
            }
        } catch (e: Exception) {
            println("Error en getDetails ($id): ${e.message}")
            MovieDetails()
        }
    }

    suspend fun getServerByEpisode(token: String?, titleId: String, season: Int, episode: Int): ServerItem? = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/servers?title_id=eq.$titleId&season_number=eq.$season&episode_number=eq.$episode&select=id,name,playable_url,referer,priority&order=priority.desc"
            unsafeClient.newCall(newRequest(url).get().build()).execute().use { resp ->
                val bodyString = resp.body?.string() ?: "[]"
                if (!resp.isSuccessful) return@withContext null
                val arr = JSONArray(bodyString)
                if (arr.length() > 0) {
                    val s = arr.getJSONObject(0)
                    return@withContext ServerItem(
                        id = s.optString("id"),
                        name = s.optString("name"),
                        playable_url = s.optString("playable_url"),
                        referer = s.optString("referer", null),
                        priority = s.optInt("priority", 0),
                        season_number = season,
                        episode_number = episode
                    )
                }
            }
        } catch (e: Exception) {
            println("Error en getServerByEpisode: ${e.message}")
        }
        null
    }
}
