package app.cuevanatv.net

import android.content.Context
import android.content.Intent
import android.util.Log
import app.cuevanatv.Auth
import app.cuevanatv.BuildConfig
import app.cuevanatv.LoginActivity
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.NewsItem
import app.cuevanatv.model.ServerItem
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class ApiClient(private val context: Context) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "Supabase_Debug"
        private const val TAG_NET = "SUPABASE_NET_ERROR"
        private const val TAG_MP = "MP_SUPABASE_DEBUG"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        fun getUnsafeClient(): OkHttpClient {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, arrayOf<TrustManager>(tm), java.security.SecureRandom())
            return OkHttpClient.Builder()
                .sslSocketFactory(sc.socketFactory, tm)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
                }
                .build()
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val response = chain.proceed(request)

        val isAuthPath = path.contains("app_users") || path.contains("login") || path.contains("check-status")
        val isFromLogin = context.javaClass.simpleName.contains("LoginActivity")

        if ((response.code == 401 || response.code == 403) && request.url.host.contains("supabase.co")) {
            Log.w(TAG, "Error de Autenticación en: $path | Código: ${response.code}")
            if (!isFromLogin && !isAuthPath) {
                Log.e(TAG, "SESIÓN EXPIRADA. Redirigiendo a Login...")
                Auth.clear(context)
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            }
        }
        response
    }

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        while (!response.isSuccessful && response.code != 401 && response.code != 403 && tryCount < 2) {
            tryCount++
            Log.w(TAG, "Petición fallida (${response.code}), reintentando $tryCount/2...")
            Thread.sleep(tryCount * 1000L)
            response.close()
            response = chain.proceed(request)
        }
        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        getUnsafeClient().newBuilder()
            .addInterceptor(authInterceptor)
            .addInterceptor(retryInterceptor)
            .build()
    }

    private fun baseUrl(): String = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
    private fun anonKey(): String = BuildConfig.SUPABASE_ANON_KEY.trim().removeSurrounding("\"")
    private fun restUrl(): String = baseUrl().trimEnd('/') + "/rest/v1"

    private fun newRequest(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey())
            .addHeader("Content-Type", "application/json")
            .addHeader("X-App-Version", "100")

        if (!url.contains("/functions/v1/")) {
            builder.addHeader("Authorization", "Bearer ${anonKey()}")
        }
        return builder
    }

    suspend fun login(email: String, pass: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encEmail = URLEncoder.encode(email.trim(), "UTF-8")
            val encPass = URLEncoder.encode(pass, "UTF-8")
            // SENIOR FIX: Solicitamos solo las columnas verificadas que existen en la DB para evitar error 400
            val url = "${restUrl()}/app_users?select=id,email,active,days_remaining,fecha_vencimiento,limite_pantallas,bypass_qr&email=eq.$encEmail&password=eq.$encPass"
            
            Log.d(TAG, "Login Attempt URL: $url")
            val request = newRequest(url).addHeader("Cache-Control", "no-cache").get().build()
            
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG_NET, "Error en Login [${response.code}]: $body")
                    return@withContext if (response.code == 401 || response.code == 403) 
                        JSONObject().put("error_type", "auth").put("code", response.code)
                    else null
                }
                
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext JSONObject()
                
                val user = arr.getJSONObject(0)
                if (user.optBoolean("bypass_qr", false)) {
                    user.put("active", true)
                    user.put("days_remaining", 999)
                }
                user
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Excepción en LOGIN: ${e.message}")
            null
        }
    }

    suspend fun checkStatus(userId: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            
            // Update last connection
            val updateUrl = "${restUrl()}/app_users?id=eq.$userId"
            val updateBody = JSONObject().put("ultima_conexion", sdf.format(Date())).toString().toRequestBody(jsonMediaType)
            okHttpClient.newCall(newRequest(updateUrl).patch(updateBody).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) { response.close() }
            })

            val url = "${restUrl()}/app_users?id=eq.$userId&select=active,days_remaining,bypass_qr"
            okHttpClient.newCall(newRequest(url).get().build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext passiveState()
                
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val status = arr.getJSONObject(0)
                    if (status.optBoolean("bypass_qr", false)) {
                        status.put("active", true)
                        status.put("days_remaining", 999)
                    }
                    return@withContext status
                }
                passiveState()
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Error en checkStatus: ${e.message}")
            passiveState()
        }
    }

    private fun passiveState() = JSONObject().put("active", true).put("days_remaining", 3).put("bypass_qr", false)

    suspend fun register(email: String, pass: String, whatsapp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/app_users"
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val expiry = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3) }.time
            
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", pass)
                .put("whatsapp", whatsapp)
                .put("active", true)
                .put("days_remaining", 3)
                .put("fecha_vencimiento", sdf.format(expiry))
                .put("limite_pantallas", 1)
                .toString()
            
            okHttpClient.newCall(newRequest(url).post(payload.toRequestBody(jsonMediaType)).build()).execute().use { 
                it.isSuccessful 
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Excepción en REGISTER: ${e.message}")
            false
        }
    }

    suspend fun getFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        try {
            val url = "${restUrl()}/titles?published=eq.true&select=id,title,description,poster_url,source_page_url,category,type,is_live,playable_url&order=created_at.desc"
            okHttpClient.newCall(newRequest(url).get().build()).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (!response.isSuccessful) return@withContext result
                
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val playable = o.optString("playable_url", "")
                    val isLive = o.optBoolean("is_live", false)
                    
                    if (isLive && (playable.isEmpty() || playable == "null")) continue
                    
                    result.add(VideoItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        imageUrl = o.optString("poster_url"),
                        streamUrl = "api://title/${o.optString("id")}",
                        playableUrl = if (playable.isNotEmpty()) playable else null,
                        sourcePageUrl = o.optString("source_page_url"),
                        category = o.optString("category"),
                        type = o.optString("type"),
                        isLive = isLive,
                        description = o.optString("description")
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Fallo crítico Feed: ${e.message}")
        }
        result
    }

    suspend fun getDetails(id: String): MovieDetails? = withContext(Dispatchers.IO) {
        try {
            val cleanId = id.replace("api://title/", "")
            val url = "${restUrl()}/titles?id=eq.$cleanId&published=eq.true&select=*,servers!servers_titleId_fkey(*)"
            
            okHttpClient.newCall(newRequest(url).addHeader("Cache-Control", "no-cache").get().build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext null
                
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                
                val obj = arr.getJSONObject(0)
                val servers = mutableListOf<ServerItem>()
                val sArr = obj.optJSONArray("servers") ?: JSONArray()
                
                for (i in 0 until sArr.length()) {
                    val s = sArr.getJSONObject(i)
                    val pUrl = s.optString("playable_url", "")
                    servers.add(ServerItem(
                        name = s.optString("name", "Servidor"),
                        playable_url = if (pUrl.isNotEmpty() && pUrl != "null") pUrl else null,
                        id = s.optString("id"),
                        page_url = s.optString("page_url"),
                        fallback_magnet = s.optString("fallback_magnet"),
                        season_number = if (!s.isNull("season_number")) s.optInt("season_number") else null,
                        episode_number = if (!s.isNull("episode_number")) s.optInt("episode_number") else null,
                        priority = s.optInt("priority", 0),
                        thumbnail_url = if (!s.isNull("thumbnail_url")) s.optString("thumbnail_url") else null,
                        duration = if (!s.isNull("duration")) s.optString("duration") else null
                    ))
                }
                
                MovieDetails(
                    description = obj.optString("description", "Sin descripción"),
                    servers = servers,
                    type = obj.optString("type", "movie"),
                    playableUrl = obj.optString("playable_url"),
                    sourcePageUrl = obj.optString("source_page_url"),
                    posterUrl = obj.optString("poster_url")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Fallo crítico Details ID $id: ${e.message}")
            null
        }
    }

    suspend fun getUserDevices(userId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val list = mutableListOf<JSONObject>()
        try {
            val url = "${restUrl()}/user_devices?user_id=eq.$userId&select=device_id"
            okHttpClient.newCall(newRequest(url).get().build()).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (!response.isSuccessful) return@withContext list
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Excepción en GET_DEVICES: ${e.message}")
        }
        list
    }

    suspend fun registerDevice(userId: String, deviceId: String, deviceModel: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${restUrl()}/user_devices"
            val payload = JSONObject()
                .put("user_id", userId)
                .put("device_id", deviceId)
                .put("device_model", deviceModel)
                .toString()
            
            okHttpClient.newCall(newRequest(url).post(payload.toRequestBody(jsonMediaType)).build()).execute().use { response ->
                response.isSuccessful || response.code == 409
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Excepción en REG_DEVICE: ${e.message}")
            false
        }
    }

    suspend fun createMercadoPagoPreference(email: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://hflcacrgwxszejlkcxsh.supabase.co/functions/v1/mercadopago-webhook"
            val payload = JSONObject().put("email", email.trim()).toString()
            val token = Auth.getToken(context) ?: anonKey()
            
            val request = newRequest(url)
                .post(payload.toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer $token")
                .build()
            
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    JSONObject(body).optString("init_point")
                } else {
                    Log.e(TAG_MP, "Fallo Checkout [Code: ${response.code}]: $body")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_MP, "Excepción crítica en Checkout: ${e.message}")
            null
        }
    }

    suspend fun getNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<NewsItem>()
        try {
            // El endpoint /api/news ya está configurado en el backend
            val url = baseUrl().trimEnd('/') + "/api/news"
            val request = Request.Builder().url(url).get().build()
            
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "[]"
                if (!response.isSuccessful) return@withContext result
                
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    result.add(NewsItem(
                        id = o.optString("id"),
                        title = o.optString("title"),
                        description = o.optString("description"),
                        image_url = o.optString("image_url"),
                        active = o.optBoolean("active", true),
                        created_at = o.optString("created_at")
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_NET, "Error obteniendo noticias: ${e.message}")
        }
        result
    }
}
