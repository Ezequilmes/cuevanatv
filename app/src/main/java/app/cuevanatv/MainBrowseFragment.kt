package app.cuevanatv

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.model.NewsItem
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.ApiClient
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainBrowseFragment : BrowseSupportFragment() {

    private var backgroundManager: BackgroundManager? = null
    private var metrics: DisplayMetrics = DisplayMetrics()
    private var autoRefreshJob: Job? = null
    private var backgroundTimer: Job? = null
    private val REFRESH_INTERVAL = 7200000L // 2 hours

    companion object {
        private val _masterItemsFlow = MutableStateFlow<List<VideoItem>>(emptyList())
        val masterItemsFlow = _masterItemsFlow.asStateFlow()

        private val _newsFlow = MutableStateFlow<List<NewsItem>>(emptyList())
        val newsFlow = _newsFlow.asStateFlow()

        fun setMasterItems(items: List<VideoItem>) {
            _masterItemsFlow.value = items
        }

        fun setNewsItems(items: List<NewsItem>) {
            _newsFlow.value = items
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("CuevanaTV", ">>> INICIANDO VERSIÓN MODERNA <<<")
        
        mainFragmentRegistry.registerFragment(PageRow::class.java, PageRowFragmentFactory())
        
        setupUIElements()
        setupEventListeners()
        rebuildMainAdapter()
        refreshData()
    }

    private fun setupUIElements() {
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.logo_horizontal)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = 0

        backgroundManager = BackgroundManager.getInstance(requireActivity()).apply {
            attach(requireActivity().window)
        }
        
        metrics = requireContext().resources.displayMetrics

        setBrowseTransitionListener(object : BrowseTransitionListener() {
            override fun onHeadersTransitionStart(withHeaders: Boolean) {
                val headersFragment = headersSupportFragment
                if (withHeaders) {
                    headersFragment?.view?.setBackgroundColor(Color.parseColor("#E6000000"))
                } else {
                    headersFragment?.view?.setBackgroundColor(0)
                }
            }
        })
    }

    private fun setupEventListeners() {
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
            if (item is VideoItem) {
                backgroundTimer?.cancel()
                backgroundTimer = lifecycleScope.launch {
                    delay(300)
                    updateBackground(item.imageUrl)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        autoRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL)
                Log.d("CuevanaTV", "Ejecutando actualización automática silenciosa...")
                refreshData(true)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        autoRefreshJob?.cancel()
    }

    fun refreshData(isSilent: Boolean = false) {
        val context = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("CuevanaTV", "Refrescando catálogo...")
                val api = ApiClient(context)
                
                // [NUEVO] Carga de noticias para el carrusel superior
                val news = api.getNews()
                setNewsItems(news)

                val items = api.getFeed()
                setMasterItems(items)
                
                withContext(Dispatchers.Main) {
                    if (isAdded && (adapter == null || !isSilent)) {
                        rebuildMainAdapter()
                    }
                }
            } catch (e: Exception) {
                Log.e("CuevanaTV", "Error carga inicial", e)
            }
        }
    }

    private fun rebuildMainAdapter() {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        rowsAdapter.add(PageRow(HeaderItem(1L, "Películas")))
        rowsAdapter.add(PageRow(HeaderItem(2L, "Series")))
        rowsAdapter.add(PageRow(HeaderItem(3L, "Deportes en Vivo")))
        rowsAdapter.add(PageRow(HeaderItem(5L, "Pelis Web")))
        rowsAdapter.add(PageRow(HeaderItem(6L, "Series Web"))) // SECCIÓN NUEVA
        rowsAdapter.add(PageRow(HeaderItem(4L, "Ajustes")))
        adapter = rowsAdapter
        
        lifecycleScope.launch {
            delay(500)
            startEntranceTransition()
        }
    }

    private fun updateBackground(uri: String?) {
        if (uri.isNullOrEmpty()) {
            backgroundManager?.setDrawable(ColorDrawable(Color.BLACK))
            return
        }

        val colorDrawable = ColorDrawable(Color.parseColor("#CC000000"))
        Glide.with(requireContext())
            .asBitmap()
            .load(uri)
            .apply(RequestOptions.bitmapTransform(BlurTransformation(25, 3)))
            .override(metrics.widthPixels / 4, metrics.heightPixels / 4)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val bitmapDrawable = BitmapDrawable(resources, resource)
                    backgroundManager?.setDrawable(LayerDrawable(arrayOf(bitmapDrawable, colorDrawable)))
                }
                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }

    private inner class PageRowFragmentFactory : BrowseSupportFragment.FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any?): Fragment {
            val row = rowObj as Row
            val id = row.headerItem.id
            return when (id) {
                1L -> GenreRowsFragment().apply {
                    arguments = Bundle().apply { putString("type", "movie") }
                }
                2L -> GenreRowsFragment().apply {
                    arguments = Bundle().apply { putString("type", "series") }
                }
                3L -> GenreRowsFragment().apply {
                    arguments = Bundle().apply { putString("type", "live") }
                }
                5L -> GenreRowsFragment().apply {
                    arguments = Bundle().apply { putString("type", "Pelis Web") }
                }
                6L -> GenreRowsFragment().apply {
                    arguments = Bundle().apply { putString("type", "Series Web") }
                }
                4L -> SettingsFragment()
                else -> RowsSupportFragment()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshJob?.cancel()
    }
}
