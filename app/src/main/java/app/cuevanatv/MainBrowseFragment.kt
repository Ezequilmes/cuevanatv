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
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.OnItemViewClickedListener
import app.cuevanatv.model.VideoItem
import app.cuevanatv.providers.StaticProvider
import app.cuevanatv.scraper.CuevanaScraper
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.ImageCardView
import com.bumptech.glide.Glide
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.launch
import android.util.Log
import android.widget.Toast
import app.cuevanatv.net.ApiClient

class MainBrowseFragment : BrowseSupportFragment() {
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var listAdapter: ArrayObjectAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        title = getString(R.string.app_name)
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                if (item is VideoItem) {
                    val intent = Intent(activity as FragmentActivity, DetailsActivity::class.java)
                    intent.putExtra("title", item.title)
                    intent.putExtra("imageUrl", item.imageUrl)
                    intent.putExtra("pageUrl", item.streamUrl)
                    startActivity(intent)
                }
            }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupRows()
        loadData()
    }

    private fun setupRows() {
        val listRowPresenter = ListRowPresenter()
        rowsAdapter = ArrayObjectAdapter(listRowPresenter)
        val cardPresenter = CardPresenter()
        listAdapter = ArrayObjectAdapter(cardPresenter)
        val header = HeaderItem(0, "Últimos")
        rowsAdapter.add(ListRow(header, listAdapter))
        adapter = rowsAdapter
        setSelectedPosition(0)
    }

    private fun loadData() {
        // 1) Mostrar contenido estático de inmediato para evitar pantalla vacía
        listAdapter.clear()
        val featured = StaticProvider.getFeatured()
        featured.forEach { listAdapter.add(it) }
        Log.d("CuevanaTV", "Cargados estáticos: ${featured.size}")
        try {
            Toast.makeText(requireContext(), "Cargados ${featured.size} destacados", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        // 2) Intentar actualizar con datos del scraper en segundo plano
        lifecycleScope.launch {
            try {
                val token = Auth.getToken(requireContext())
                val items = if (!token.isNullOrEmpty() && app.cuevanatv.BuildConfig.SUPABASE_URL.isNotEmpty())
                    ApiClient(requireContext()).getFeed(token)
                else
                    CuevanaScraper().fetchLatestMovies()
                if (items.isNotEmpty()) {
                    listAdapter.clear()
                    items.forEach { listAdapter.add(it) }
                    Log.d("CuevanaTV", "Cargados online: ${items.size}")
                    try {
                        Toast.makeText(requireContext(), "Actualizados ${items.size} online", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) { /* mantener estáticos */ }
        }
    }
}

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setMainImageDimensions(300, 450)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as VideoItem
        val card = viewHolder.view as ImageCardView
        card.titleText = video.title
        card.setMainImage(ColorDrawable(Color.DKGRAY))
        Glide.with(card.context)
            .load(video.imageUrl)
            .centerCrop()
            .into(card.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
    }
}
