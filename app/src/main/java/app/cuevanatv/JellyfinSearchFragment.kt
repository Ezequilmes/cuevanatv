package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.JellyfinClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JellyfinSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var resultsAdapter: ArrayObjectAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Buscar"
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        resultsAdapter = ArrayObjectAdapter(CardPresenter())
        rowsAdapter.add(ListRow(HeaderItem(0, "Resultados"), resultsAdapter))
        setSearchResultProvider(this)
        setOnItemViewClickedListener(
            OnItemViewClickedListener { _, item, _, _ ->
                if (item !is VideoItem) return@OnItemViewClickedListener
                val sUrl = item.streamUrl ?: ""
                if (sUrl.startsWith("jellyfin://browse/")) {
                    val parentId = sUrl.removePrefix("jellyfin://browse/")
                    startActivity(
                        Intent(requireContext(), JellyfinBrowseActivity::class.java)
                            .putExtra("parentId", parentId)
                            .putExtra("title", item.title)
                    )
                } else {
                    val intent = Intent(activity as FragmentActivity, DetailsActivity::class.java)
                    intent.putExtra("title", item.title)
                    intent.putExtra("imageUrl", item.imageUrl)
                    intent.putExtra("pageUrl", item.streamUrl)
                    startActivity(intent)
                }
            }
        )
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        runSearch(newQuery)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        runSearch(query)
        return true
    }

    private fun runSearch(query: String) {
        val q = query.trim()
        searchJob?.cancel()
        if (q.length < 2) {
            resultsAdapter.clear()
            return
        }
        searchJob = lifecycleScope.launch {
            delay(250)
            try {
                val jellyfin = JellyfinClient(requireContext())
                if (!jellyfin.isConfigured()) {
                    resultsAdapter.clear()
                    return@launch
                }
                val items = jellyfin.search(q)
                resultsAdapter.clear()
                items.forEach { resultsAdapter.add(it) }
            } catch (_: Exception) {
                resultsAdapter.clear()
            }
        }
    }
}
