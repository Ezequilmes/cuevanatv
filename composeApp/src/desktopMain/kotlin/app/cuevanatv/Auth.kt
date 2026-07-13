package app.cuevanatv

import java.util.*
import java.util.prefs.Preferences

actual object Auth {
    private val prefs = Preferences.userRoot().node("app/cuevanatv")
    
    private const val KEY_TOKEN = "access_token"
    private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
    private const val KEY_ACTIVE = "user_active"
    private const val KEY_BYPASS_QR = "bypass_qr"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_EXPIRY_DATE = "expiry_date"

    private var memoryIsActive: Boolean? = null
    private var memoryBypassQr: Boolean? = null
    private var memoryEmail: String? = null
    private var memoryExpiryDate: String? = null

    actual fun saveToken(token: String, email: String?, userId: String?) {
        prefs.put(KEY_TOKEN, token)
        if (email != null) {
            prefs.put(KEY_EMAIL, email)
            memoryEmail = email
        }
        if (userId != null) {
            prefs.put(KEY_SUPABASE_USER_ID, userId)
        }
    }

    actual fun getUserId(): String = prefs.get(KEY_SUPABASE_USER_ID, "")

    actual fun getEmail(): String = memoryEmail ?: prefs.get(KEY_EMAIL, "invitado@cuevana.tv")

    actual fun getToken(): String? = prefs.get(KEY_TOKEN, null)

    actual fun saveUserStatus(active: Boolean, bypassQr: Boolean, expiryDate: String?) {
        memoryIsActive = active
        memoryBypassQr = bypassQr
        prefs.putBoolean(KEY_ACTIVE, active)
        prefs.putBoolean(KEY_BYPASS_QR, bypassQr)
        
        if (expiryDate != null) {
            prefs.put(KEY_EXPIRY_DATE, expiryDate)
            memoryExpiryDate = expiryDate
        }
    }

    actual fun getExpiryDate(): String? {
        return memoryExpiryDate ?: prefs.get(KEY_EXPIRY_DATE, null).also { memoryExpiryDate = it }
    }

    actual fun isUserActive(): Boolean {
        return memoryIsActive ?: prefs.getBoolean(KEY_ACTIVE, false).also { memoryIsActive = it }
    }

    actual fun getBypassQr(): Boolean {
        return memoryBypassQr ?: prefs.getBoolean(KEY_BYPASS_QR, false).also { memoryBypassQr = it }
    }

    actual fun clear() {
        clearMemoryCache()
        prefs.remove(KEY_TOKEN)
        prefs.remove(KEY_SUPABASE_USER_ID)
        prefs.remove(KEY_ACTIVE)
        prefs.remove(KEY_BYPASS_QR)
        prefs.remove(KEY_EMAIL)
        prefs.remove(KEY_EXPIRY_DATE)
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
