package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.MovieDetails
import app.cuevanatv.model.ServerItem
import app.cuevanatv.scraper.CuevanaScraper
import app.cuevanatv.net.ApiClient
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import android.widget.Toast

class MovieDetailsFragment : DetailsSupportFragment() {
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var serversAdapter: ArrayObjectAdapter
    private var titleArg: String? = null
    private var imageArg: String? = null
    private var pageUrlArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleArg = arguments?.getString("title")
        imageArg = arguments?.getString("imageUrl")
        pageUrlArg = arguments?.getString("pageUrl")
        title = titleArg ?: getString(R.string.app_name)
        setupRows()
        loadDetails()
        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                if (item is ServerItem) {
                    Toast.makeText(requireContext(), "Resolviendo enlace…", Toast.LENGTH_SHORT).show()
                    val intent = Intent(activity, PlayerActivity::class.java)
                    val url = item.url ?: pageUrlArg
                    if (url != null) {
                        if (url.endsWith(".m3u8") || url.endsWith(".mp4")) {
                            intent.putExtra("url", url)
                        } else {
                            intent.putExtra("pageUrl", url)
                        }
                        startActivity(intent)
                    }
                }
            }
    }

    private fun setupRows() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val descAdapter = ArrayObjectAdapter(DescriptionPresenter(imageArg))
        val descRow = ListRow(HeaderItem(0, "Descripción"), descAdapter)
        serversAdapter = ArrayObjectAdapter(ServerPresenter())
        val serversRow = ListRow(HeaderItem(1, "Servidores"), serversAdapter)
        rowsAdapter.add(descRow)
        rowsAdapter.add(serversRow)
        adapter = rowsAdapter
    }

    private fun loadDetails() {
        val url = pageUrlArg ?: return
        lifecycleScope.launch {
            val details: MovieDetails =
                if (url.startsWith("api://title/")) {
                    val id = url.removePrefix("api://title/")
                    val token = Auth.getToken(requireContext())
                    if (!token.isNullOrEmpty() && app.cuevanatv.BuildConfig.SUPABASE_URL.isNotEmpty()) {
                        try { ApiClient(requireContext()).getDetails(token, id) }
                        catch (_: Exception) { MovieDetails("No se pudo cargar la descripción", emptyList()) }
                    } else {
                        MovieDetails("No disponible", emptyList())
                    }
                } else {
                    try { CuevanaScraper().fetchMovieDetails(url) }
                    catch (_: Exception) { MovieDetails("No se pudo cargar la descripción", emptyList()) }
                }
            val descRow = rowsAdapter.get(0) as ListRow
            val descAdapter = descRow.adapter as ArrayObjectAdapter
            descAdapter.clear()
            descAdapter.add(details.description)
            serversAdapter.clear()
            details.servers.forEach { serversAdapter.add(it) }
        }
    }
}

class DescriptionPresenter(private val imageUrl: String?) : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val v = ImageCardView(parent.context)
        v.isFocusable = false
        v.setMainImageDimensions(300, 450)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val card = viewHolder.view as ImageCardView
        card.titleText = "Sinopsis"
        card.contentText = item as String
        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(card.context).load(imageUrl).centerCrop().into(card.mainImageView)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
    }
}

class ServerPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val v = android.widget.TextView(parent.context)
        v.isFocusable = true
        v.isFocusableInTouchMode = true
        v.textSize = 18f
        v.setPadding(32, 16, 32, 16)
        v.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            160
        )
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val v = viewHolder.view as android.widget.TextView
        val s = item as ServerItem
        v.text = s.name
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
    }
}
