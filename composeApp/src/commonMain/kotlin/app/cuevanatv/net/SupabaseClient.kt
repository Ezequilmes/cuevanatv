package app.cuevanatv.net

import app.cuevanatv.Config
import io.github.jan_tenner.supabase.createSupabaseClient
import io.github.jan_tenner.supabase.postgrest.Postgrest
import io.github.jan_tenner.supabase.postgrest.from
import io.github.jan_tenner.supabase.postgrest.query.Order
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = Config.SUPABASE_URL,
        supabaseKey = Config.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
    }

    suspend fun getFeed(category: String?): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val query = client.from("titles")
                .select {
                    filter {
                        eq("published", true)
                        if (!category.isNullOrEmpty() && category != "Todas") {
                            eq("category", category)
                        }
                    }
                    order("created_at", order = Order.DESCENDING)
                }
            
            // Mapeo manual a VideoItem para asegurar compatibilidad
            val data = query.decodeList<VideoItemMap>()
            data.map { it.toVideoItem() }
        } catch (e: Exception) {
            println("Error Supabase Feed: ${e.message}")
            emptyList()
        }
    }
}

// Clase auxiliar para decodificación de Supabase
@kotlinx.serialization.Serializable
data class VideoItemMap(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val poster_url: String? = null,
    val source_page_url: String? = null,
    val category: String? = null,
    val type: String? = "movie",
    val is_live: Boolean = false,
    val playable_url: String? = null
) {
    fun toVideoItem() = VideoItem(
        id = id,
        title = title,
        description = description,
        imageUrl = poster_url,
        sourcePageUrl = source_page_url,
        category = category,
        type = type ?: "movie",
        isLive = is_live,
        playableUrl = if (!playable_url.isNullOrBlank()) playable_url else null,
        streamUrl = "api://title/$id"
    )
}
