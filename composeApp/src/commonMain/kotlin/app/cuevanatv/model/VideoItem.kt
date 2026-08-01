package app.cuevanatv.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoItem(
    val id: String? = null,
    val title: String? = null,
    @SerialName("poster_url") val imageUrl: String? = null,
    val streamUrl: String? = null,
    @SerialName("playable_url") val playableUrl: String? = null,
    @SerialName("source_page_url") val sourcePageUrl: String? = null,
    val category: String? = null,
    val type: String? = "movie",
    @SerialName("is_live") val isLive: Boolean = false,
    val description: String? = null
)
