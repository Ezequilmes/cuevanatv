package app.cuevanatv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieDetails(
    val description: String? = "",
    val servers: List<ServerItem> = emptyList(),
    val type: String? = "movie",
    @SerialName("playable_url") val playableUrl: String? = null,
    @SerialName("source_page_url") val sourcePageUrl: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null
)
