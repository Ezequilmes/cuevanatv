package app.cuevanatv

import android.content.Context
import android.content.SharedPreferences

object Auth {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_ACTIVE = "user_active"

    fun saveSession(context: Context, token: String, email: String, active: Boolean) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .putBoolean(KEY_ACTIVE, active)
            .apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)
    fun getEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)
    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
