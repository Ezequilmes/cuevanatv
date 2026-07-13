package app.cuevanatv

import java.util.Date

expect object Auth {
    fun saveToken(token: String, email: String? = null, userId: String? = null)
    fun getUserId(): String
    fun getEmail(): String
    fun getToken(): String?
    fun saveUserStatus(active: Boolean, bypassQr: Boolean, expiryDate: String? = null)
    fun getExpiryDate(): String?
    fun isUserActive(): Boolean
    fun getBypassQr(): Boolean
    fun clear()
    fun clearMemoryCache()
    fun parseIsoDate(dateStr: String?): Date?
}
