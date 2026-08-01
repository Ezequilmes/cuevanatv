package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.launch

class LiveGridFragment : VerticalGridSupportFragment() {

    private lateinit var mAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Deportes en Vivo"
        setupFragment()
        setupEventListeners()
        loadData()
    }

    private fun setupFragment() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = 3
        setGridPresenter(gridPresenter)

        mAdapter = ArrayObjectAdapter(LiveCardPresenter())
        adapter = mAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is VideoItem) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("primaryUrl", item.streamUrl)
                    putExtra("isLive", true)
                    putExtra("title", item.title)
                }
                startActivity(intent)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val token = Auth.getToken(requireContext()) ?: return@launch
            val items = ApiClient(requireContext()).getFeed(token)
            mAdapter.addAll(0, items)
        }
    }
}
