package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.VideoItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class GenreRowsFragment : RowsSupportFragment() {
    private var rowsAdapter: ArrayObjectAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        verticalGridView.setPadding(0, 40, 56, 40)
        verticalGridView.clipToPadding = false

        val type = arguments?.getString("type") ?: "movie"
        setupRows()

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is VideoItem) {
                if (item.streamUrl == "action://view_all_live") {
                    startActivity(Intent(requireContext(), LiveGridActivity::class.java))
                    return@OnItemViewClickedListener
                }

                if (item.isLive) {
                    val intent = Intent(requireContext(), PlayerActivity::class.java)
                    intent.putExtra("video_url", item.playableUrl)
                    intent.putExtra("sourcePageUrl", item.sourcePageUrl)
                    intent.putExtra("is_live", true)
                    intent.putExtra("title", item.title)
                    startActivity(intent)
                    return@OnItemViewClickedListener
                }

                val intent = Intent(requireContext(), DetailsActivity::class.java)
                intent.putExtra("title", item.title)
                intent.putExtra("imageUrl", item.imageUrl)
                intent.putExtra("pageUrl", item.streamUrl)
                intent.putExtra("type", item.type)
                startActivity(intent)
            }
        }

        lifecycleScope.launch {
            MainBrowseFragment.masterItemsFlow.collectLatest { list ->
                if (list.isNotEmpty()) {
                    updateRowsData(type, list)
                }
            }
        }
    }

    private fun setupRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter(4))
        adapter = rowsAdapter
    }

    private fun updateRowsData(type: String, allItems: List<VideoItem>) {
        val filtered = if (type == "live") {
            allItems.filter { it.isLive }
        } else {
            allItems.filter { !it.isLive && it.type?.lowercase(Locale.ROOT) == type }
        }

        val grouped = filtered.groupBy { it.category ?: if (type == "live") "Canales en Vivo" else "Novedades" }
        
        rowsAdapter?.clear()

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
