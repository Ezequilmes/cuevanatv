package app.cuevanatv.model

data class ServerItem(
    val name: String? = "Servidor",
    val playable_url: String? = null,
    val id: String? = null,
    val referer: String? = null,
    val fallbackMagnet: String? = null,
    val season_number: Int? = null,
    val episode_number: Int? = null,
    val priority: Int = 0
)
