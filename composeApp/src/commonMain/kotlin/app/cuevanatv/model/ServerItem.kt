package app.cuevanatv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerItem(
    val id: String? = null,
    @SerialName("title_id") val titleId: String? = null,
    val name: String? = "Servidor",
    @SerialName("playable_url") val playable_url: String? = null,
    @SerialName("page_url") val page_url: String? = null,
    @SerialName("season_number") val season_number: Int? = null,
    @SerialName("episode_number") val episode_number: Int? = null,
    val priority: Int = 0,
    val referer: String? = null
)
