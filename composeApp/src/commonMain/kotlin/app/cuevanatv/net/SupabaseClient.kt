package app.cuevanatv.net

import app.cuevanatv.Config
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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
            println("[SUPABASE] Cargando feed para categoría: $category")
            val query = client.from("titles")
                .select {
                    filter {
                        eq("published", true)
                        if (!category.isNullOrEmpty() && category != "Todas") {
                            ilike("category", "%$category%")
                        }
                    }
                    order("created_at", order = Order.DESCENDING)
                    // Eliminamos cualquier limit accidental si existiera en versiones futuras del SDK
                    // y nos aseguramos de que el conteo de la respuesta sea verificado
                }
            
            val data = query.decodeList<VideoItemMap>()
            println("[SUPABASE] Items recuperados: ${data.size}")
            data.map { it.toVideoItem() }
        } catch (e: Exception) {
            println("Error Supabase Feed: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getEpisodes(seriesId: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        try {
            val query = client.from("servers")
                .select {
                    filter {
                        eq("title_id", seriesId)
                    }
                    order("season_number", order = Order.ASCENDING)
                    order("episode_number", order = Order.ASCENDING)
                }
            query.decodeList<EpisodeItem>()
        } catch (e: Exception) {
            println("Error Supabase Episodes: ${e.message}")
            emptyList()
        }
    }
}

@kotlinx.serialization.Serializable
data class EpisodeItem(
    val id: String? = null,
    val title: String? = null,
    @kotlinx.serialization.SerialName("season_number")
    val season: Int? = 1,
    @kotlinx.serialization.SerialName("episode_number")
    val episode: Int? = 1,
    val playable_url: String? = null,
    val page_url: String? = null,
    val server_name: String? = "Server"
)

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
