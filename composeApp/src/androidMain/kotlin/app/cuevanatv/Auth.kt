package app.cuevanatv

import android.content.Context
import android.content.SharedPreferences
import java.util.*

actual object Auth {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_JELLYFIN_USER_ID = "jellyfin_user_id"
    private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
    private const val KEY_ACTIVE = "user_active"
    private const val KEY_BYPASS_QR = "bypass_qr"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_EXPIRY_DATE = "expiry_date"

    private var memoryIsActive: Boolean? = null
    private var memoryBypassQr: Boolean? = null
    private var memoryEmail: String? = null
    private var memoryExpiryDate: String? = null

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(): SharedPreferences {
        return appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) 
            ?: throw IllegalStateException("Auth not initialized with context")
    }

    actual fun saveToken(token: String, email: String?, userId: String?) {
        val editor = prefs().edit().putString(KEY_TOKEN, token)
        if (email != null) {
            editor.putString(KEY_EMAIL, email)
            memoryEmail = email
        }
        if (userId != null) {
            editor.putString(KEY_SUPABASE_USER_ID, userId)
        }
        editor.apply()
    }

    actual fun getUserId(): String = prefs().getString(KEY_SUPABASE_USER_ID, "") ?: ""

    actual fun getEmail(): String = memoryEmail ?: prefs().getString(KEY_EMAIL, "invitado@cuevana.tv") ?: "invitado@cuevana.tv"

    actual fun getToken(): String? = prefs().getString(KEY_TOKEN, null)

    actual fun saveUserStatus(active: Boolean, bypassQr: Boolean, expiryDate: String?) {
        memoryIsActive = active
        memoryBypassQr = bypassQr
        val editor = prefs().edit()
            .putBoolean(KEY_ACTIVE, active)
            .putBoolean(KEY_BYPASS_QR, bypassQr)
        
        if (expiryDate != null) {
            editor.putString(KEY_EXPIRY_DATE, expiryDate)
            memoryExpiryDate = expiryDate
        }
        editor.apply()
    }

    actual fun getExpiryDate(): String? {
        return memoryExpiryDate ?: prefs().getString(KEY_EXPIRY_DATE, null).also { memoryExpiryDate = it }
    }

    actual fun isUserActive(): Boolean {
        return memoryIsActive ?: prefs().getBoolean(KEY_ACTIVE, false).also { memoryIsActive = it }
    }

    actual fun getBypassQr(): Boolean {
        return memoryBypassQr ?: prefs().getBoolean(KEY_BYPASS_QR, false).also { memoryBypassQr = it }
    }

    actual fun clear() {
        clearMemoryCache()
        prefs().edit()
            .remove(KEY_TOKEN)
            .remove(KEY_JELLYFIN_USER_ID)
            .remove(KEY_ACTIVE)
            .remove(KEY_BYPASS_QR)
            .apply()
    }

    actual fun clearMemoryCache() {
        memoryIsActive = null
        memoryBypassQr = null
        memoryEmail = null
        memoryExpiryDate = null
    }

    actual fun parseIsoDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank() || dateStr == "null") return null
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(dateStr)
            } catch (_: Exception) {
            }
        }
        return null
    }
}
