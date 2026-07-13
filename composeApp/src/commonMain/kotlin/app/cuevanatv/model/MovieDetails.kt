package app.cuevanatv.model

data class MovieDetails(
    val description: String? = "",
    val servers: List<ServerItem> = emptyList(),
    val type: String? = "movie",
    val playableUrl: String? = null,
    val sourcePageUrl: String? = null,
    val posterUrl: String? = null
)
