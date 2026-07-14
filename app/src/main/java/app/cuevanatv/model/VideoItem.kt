package app.cuevanatv.model

data class VideoItem(
    val title: String,
    val imageUrl: String?,
    val streamUrl: String,
    val type: String? = "movie",
    val category: String? = null
)

