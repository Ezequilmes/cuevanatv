package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.R
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.JellyfinClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class JellyfinBrowseFragment : BrowseSupportFragment() {
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var actionsAdapter: ArrayObjectAdapter
    private lateinit var itemsAdapter: ArrayObjectAdapter
    private var refreshJob: Job? = null

    private val refreshAction = VideoItem(title = "Actualizar", imageUrl = "", streamUrl = "action://refresh")
    private val searchAction = VideoItem(title = "Buscar", imageUrl = "", streamUrl = "action://search")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = arguments?.getString("title") ?: "Jellyfin"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        setOnSearchClickedListener {
            startActivity(Intent(requireContext(), JellyfinSearchActivity::class.java))
        }
        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                if (item !is VideoItem) return@OnItemViewClickedListener
                val sUrl = item.streamUrl ?: ""
                when {
                    sUrl == refreshAction.streamUrl -> {
                        loadItems(force = true)
                        return@OnItemViewClickedListener
                    }

                    sUrl == searchAction.streamUrl -> {
                        startActivity(Intent(requireContext(), JellyfinSearchActivity::class.java))
                        return@OnItemViewClickedListener
                    }

                    sUrl.startsWith("jellyfin://browse/") -> {
                        val parentId = sUrl.removePrefix("jellyfin://browse/")
                        startActivity(
                            Intent(requireContext(), JellyfinBrowseActivity::class.java)
                                .putExtra("parentId", parentId)
                                .putExtra("title", item.title)
                        )
                        return@OnItemViewClickedListener
                    }

                    else -> {
                        val intent = Intent(activity as FragmentActivity, DetailsActivity::class.java)
                        intent.putExtra("title", item.title)
                        intent.putExtra("imageUrl", item.imageUrl)
                        intent.putExtra("pageUrl", item.streamUrl)
                        startActivity(intent)
                    }
                }
            }

        setupRows()
        loadItems(force = true)
    }

    private fun setupRows() {
        val cardPresenter = CardPresenter()
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        actionsAdapter = ArrayObjectAdapter(cardPresenter).apply {
            add(refreshAction)
            add(searchAction)
        }
        itemsAdapter = ArrayObjectAdapter(cardPresenter)
        rowsAdapter.add(ListRow(HeaderItem(0, "Acciones"), actionsAdapter))
        rowsAdapter.add(ListRow(HeaderItem(1, "Contenido"), itemsAdapter))
        adapter = rowsAdapter
        setSelectedPosition(1)
    }

    private fun loadItems(force: Boolean) {
        val parentId = arguments?.getString("parentId")?.trim().orEmpty()
        if (parentId.isBlank()) return
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            try {
                val jellyfin = JellyfinClient(requireContext())
                val items = jellyfin.browse(parentId)
                if (force || items.isNotEmpty()) {
                    itemsAdapter.clear()
                    items.forEach { itemsAdapter.add(it) }
                }
            } catch (_: Exception) {
            }
        }
    }
}
