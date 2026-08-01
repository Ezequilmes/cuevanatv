package app.cuevanatv

import android.content.Context
import android.content.SharedPreferences

object Auth {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_ACTIVE = "user_active"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_JELLYFIN_USER_ID = "jellyfin_user_id"

    fun saveSession(context: Context, token: String, email: String, active: Boolean, userId: String = "") {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    // ALIAS PARA COMPATIBILIDAD CON REGISTER_ACTIVITY
    fun saveToken(context: Context, token: String, email: String, userId: String = "") {
        saveSession(context, token, email, true, userId)
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getEmail(context: Context): String = prefs(context).getString(KEY_EMAIL, "").orEmpty()
    fun getUserId(context: Context): String = prefs(context).getString(KEY_USER_ID, "").orEmpty()
    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun getJellyfinUserId(context: Context): String? = prefs(context).getString(KEY_JELLYFIN_USER_ID, null)

    fun saveJellyfinUserId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_JELLYFIN_USER_ID, id).apply()
    }

    fun saveUserStatus(context: Context, active: Boolean, bypassQr: Boolean = false) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
