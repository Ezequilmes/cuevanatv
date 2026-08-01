package app.cuevanatv.model

data class VideoItem(
    val id: String? = null,
    val title: String? = null,
    val imageUrl: String? = null,
    val streamUrl: String? = null,
    val playableUrl: String? = null,
    val sourcePageUrl: String? = null,
    val category: String? = null,
    val type: String? = null,
    val isLive: Boolean = false,
    val description: String? = null
)
