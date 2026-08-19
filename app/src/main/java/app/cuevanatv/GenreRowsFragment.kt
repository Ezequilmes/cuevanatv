package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.NewsItem
import app.cuevanatv.model.VideoItem
import app.cuevanatv.NewsCardPresenter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import android.app.AlertDialog
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class GenreRowsFragment : RowsSupportFragment() {
    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        verticalGridView.setPadding(0, 40, 56, 40)
        verticalGridView.clipToPadding = false

        val type = arguments?.getString("type") ?: "movie"
        setupRows()

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is NewsItem) {
                showNewsDialog(item)
                return@OnItemViewClickedListener
            }
            if (item is VideoItem) {
                if (item.streamUrl == "action://view_all_live") {
                    startActivity(Intent(requireContext(), LiveGridActivity::class.java))
                    return@OnItemViewClickedListener
                }

                val itemType = item.type?.lowercase(Locale.ROOT) ?: "movie"
                val pUrl = item.playableUrl
                val hasLink = pUrl != null && pUrl != "" && pUrl != "null"
                
                val isSeries = itemType == "series" || itemType == "series web"

                if (!isSeries && ((itemType == "movie" && hasLink) || itemType == "live" || item.isLive || itemType == "web_scraper" || itemType == "pelis web")) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("url", item.playableUrl)
                        putExtra("title", item.title)
                        putExtra("is_live", item.isLive)
                        putExtra("type", item.type)
                        putExtra("referer", item.sourcePageUrl)
                    }
                    startActivity(intent)
                } else if (isSeries || (itemType == "movie" && !hasLink)) {
                    val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                        putExtra("title", item.title)
                        putExtra("imageUrl", item.imageUrl)
                        putExtra("pageUrl", item.streamUrl)
                        putExtra("type", item.type)
                    }
                    startActivity(intent)
                }
            }
        }

        lifecycleScope.launch {
            MainBrowseFragment.masterItemsFlow.collectLatest { list ->
                if (list.isNotEmpty()) {
                    updateRowsData(type, list, MainBrowseFragment.newsFlow.value)
                }
            }
        }

        // [NUEVO] Suscripción a noticias
        lifecycleScope.launch {
            MainBrowseFragment.newsFlow.collectLatest { newsList: List<NewsItem> ->
                if (type == "movie") {
                    updateRowsData(type, MainBrowseFragment.masterItemsFlow.value, newsList)
                }
            }
        }
    }

    private fun showNewsDialog(news: NewsItem) {
        val context = requireContext()
        val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val inflater = android.view.LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_news_detail, null)
        
        val img = view.findViewById<ImageView>(R.id.news_dialog_img)
        val title = view.findViewById<TextView>(R.id.news_dialog_title)
        val desc = view.findViewById<TextView>(R.id.news_dialog_desc)
        val btnClose = view.findViewById<Button>(R.id.btn_close_news)

        title.text = news.title
        desc.text = news.description
        
        try {
            com.bumptech.glide.Glide.with(this).load(news.image_url).into(img)
        } catch (e: Exception) {}

        val dialog = builder.setView(view).create()
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        btnClose.requestFocus()
    }

    private fun setupRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter(4))
        adapter = rowsAdapter
    }

    private fun updateRowsData(type: String, allItems: List<VideoItem>, newsList: List<NewsItem> = emptyList()) {
        val typeLower = type.lowercase(Locale.ROOT)
        // ... (resto de lógica de filtrado)
        val filtered = when {
            typeLower == "live" -> {
                allItems.filter { it.isLive }
            }
            typeLower == "pelis web" -> {
                allItems.filter { it.type?.lowercase(Locale.ROOT) == "pelis web" }
            }
            typeLower == "series web" -> {
                allItems.filter { it.type?.lowercase(Locale.ROOT) == "series web" }
            }
            typeLower == "movie" -> {
                // Películas normales (excluyendo pelis web)
                allItems.filter { 
                    val t = it.type?.lowercase(Locale.ROOT) ?: "movie"
                    !it.isLive && t == "movie" && it.category != "Pelis Web"
                }
            }
            typeLower == "series" -> {
                // Series normales (excluyendo series web)
                allItems.filter { 
                    val t = it.type?.lowercase(Locale.ROOT) ?: ""
                    t == "series" && it.category != "Series Web"
                }
            }
            else -> {
                allItems.filter { !it.isLive && it.type?.lowercase(Locale.ROOT) == typeLower }
            }
        }

        val grouped = filtered.groupBy { it.category ?: if (type == "live") "Canales en Vivo" else "Novedades" }
        
        rowsAdapter?.clear()

        // [NUEVO] Carrusel de noticias en la parte superior de Películas
        if (typeLower == "movie" && newsList.isNotEmpty()) {
            val newsAdapter = ArrayObjectAdapter(NewsCardPresenter())
            newsAdapter.addAll(0, newsList)
            rowsAdapter?.add(ListRow(HeaderItem(0L, "Últimas Noticias"), newsAdapter))
        }

        if (type == "live") {
            val quickAccessAdapter = ArrayObjectAdapter(LiveCardPresenter())
            quickAccessAdapter.add(VideoItem(
                title = "CuevanaTV | EXPLORAR TODO : Grilla Completa",
                streamUrl = "action://view_all_live",
                isLive = true,
                category = "IPTV"
            ))
            rowsAdapter?.add(ListRow(HeaderItem(300L, "Accesos Rápidos"), quickAccessAdapter))
        }

        var headerId = if (type == "live") 301L else 100L
        grouped.toSortedMap().forEach { (category, items) ->
            val listRowAdapter = ArrayObjectAdapter(if (type == "live") LiveCardPresenter() else CardPresenter())
            listRowAdapter.addAll(0, items)
            rowsAdapter?.add(ListRow(HeaderItem(headerId++, category), listRowAdapter))
        }
    }
}
