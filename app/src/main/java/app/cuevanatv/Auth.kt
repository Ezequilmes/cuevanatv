package app.cuevanatv

import android.content.Context
import android.content.SharedPreferences

object Auth {
    private const val PREFS = "auth_prefs"
    private const val KEY_TOKEN = "access_token"

    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TOKEN).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
