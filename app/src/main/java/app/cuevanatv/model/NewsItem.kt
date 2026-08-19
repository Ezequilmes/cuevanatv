package app.cuevanatv.model

import java.io.Serializable

data class NewsItem(
    val id: String,
    val title: String,
    val description: String?,
    val image_url: String?,
    val active: Boolean,
    val created_at: String?
) : Serializable
