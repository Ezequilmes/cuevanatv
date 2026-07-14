package app.cuevanatv.model

data class ServerItem(
    val name: String?,
    val playable_url: String?,
    val id: String? = null,
    val episode_number: Int? = null,
    val referer: String? = null
) {
    // Para compatibilidad con código que usa .url
    val url: String? get() = playable_url
}

