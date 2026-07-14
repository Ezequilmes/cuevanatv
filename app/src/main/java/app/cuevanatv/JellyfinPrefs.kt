package app.cuevanatv

import android.content.Context
import android.content.SharedPreferences

object JellyfinPrefs {
    private const val PREFS = "jellyfin_prefs"
    private const val KEY_URL = "url"
    private const val KEY_API_KEY = "api_key"

    fun isConfigured(context: Context): Boolean =
        getUrl(context).isNotBlank() && getApiKey(context).isNotBlank()

    fun getUrl(context: Context): String = prefs(context).getString(KEY_URL, "").orEmpty().trim()

    fun getApiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "").orEmpty().trim()

    fun save(context: Context, url: String, apiKey: String) {
        prefs(context).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .apply()
        Auth.saveJellyfinUserId(context, "")
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        Auth.saveJellyfinUserId(context, "")
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

