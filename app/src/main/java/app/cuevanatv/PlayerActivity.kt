package app.cuevanatv

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import app.cuevanatv.R
import app.cuevanatv.net.InvisibleWebScraper
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ARQUITECTURA HÍBRIDA (Senior Optimized)
 * VOD (Películas/Series) -> LibVLC (Máxima estabilidad)
 * LIVE (Deportes/Canales) -> ExoPlayer (Resolución asíncrona de Tokens)
 * 
 * FIX: Gestión estricta de memoria para evitar bloqueos al cambiar canales.
 */
@UnstableApi
class PlayerActivity : FragmentActivity() {

    // Motores de reproducción
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var exoPlayer: ExoPlayer? = null
    
    private lateinit var vlcVideoLayout: VLCVideoLayout
    private lateinit var exoPlayerView: PlayerView

// --- UI CONTROLS ---
    private lateinit var controlsContainer: View
    private lateinit var btnPlayPause: Button
    private lateinit var btnTracks: Button
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var loadingOverlay: View
    private lateinit var btnBack: ImageButton

    // Paywall
    private lateinit var paywallContainer: View
    private lateinit var ivPaywallQr: ImageView
    private lateinit var btnMercadoPago: Button
    private lateinit var btnPaywallClose: Button

    private var isControlsVisible = false
    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }

    private var videoUrl: String? = null
    private var isLive: Boolean = false
    private var scraper: InvisibleWebScraper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Inicializar vistas
        vlcVideoLayout = findViewById(R.id.player_view)
        exoPlayerView = findViewById(R.id.exo_player_view)
        
        controlsContainer = findViewById(R.id.controls_container)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnTracks = findViewById(R.id.btn_tracks)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        seekBar = findViewById(R.id.seek_bar)
        loadingOverlay = findViewById(R.id.loading_overlay)
        btnBack = findViewById(R.id.btn_back)

        paywallContainer = findViewById(R.id.paywall_container)
        ivPaywallQr = findViewById(R.id.iv_paywall_qr)
        btnMercadoPago = findViewById(R.id.btn_mercadopago)
        btnPaywallClose = findViewById(R.id.btn_paywall_close)

        // Configurar navegación Paywall
        btnMercadoPago.nextFocusDownId = R.id.btn_paywall_close
        btnPaywallClose.nextFocusUpId = R.id.btn_mercadopago

        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnTracks.setOnClickListener { showTracksDialog() }
        btnPaywallClose.setOnClickListener { finish() }
        
        btnMercadoPago.setOnClickListener {
            val email = Auth.getEmail(this)
            ejecutarCobroMercadoPago(email)
        }

        vlcVideoLayout.setOnClickListener { toggleControls() }
        exoPlayerView.setOnClickListener { toggleControls() }

        setupSeekBar()
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // REGLA DE ORO: Limpieza de reproducción antes de iniciar nueva carga
        // Mantener el motor LibVLC activo para evitar reinicializaciones lentas en MediaTek
        stopPlaybackButKeepEngine()
        showLoading(true)

        // Obtener datos del Intent - Normalización estricta
        videoUrl = intent.getStringExtra("url") ?: intent.getStringExtra("video_url") ?: intent.getStringExtra("primaryUrl")
        isLive = intent.getBooleanExtra("is_live", false) || intent.getBooleanExtra("isLive", false)
        val type = intent.getStringExtra("type") ?: "movie"

        Log.d("PlayerActivity", ">>> REPRODUCCIÓN - isLive: $isLive | Type: $type | URL: $videoUrl")
        
        // DEPURACIÓN VISUAL PARA TV
        Toast.makeText(this, "URL Recibida: $videoUrl", Toast.LENGTH_LONG).show()

        // --- ENRUTADOR DE ARQUITECTURA SENIOR ---
        when {
            isLive -> {
                resolveLiveTokenAndPlay(videoUrl)
            }
            videoUrl != null -> {
                playWithVlc(videoUrl!!)
            }
            else -> {
                val slug = intent.getStringExtra("slug")
                if (slug != null) {
                    iniciarScraping(slug)
                } else {
                    showLoading(false)
                    Toast.makeText(this, "Error: Enlace no válido", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun stopPlaybackButKeepEngine() {
        Log.d("PlayerActivity", "♻️ Reseteando reproducción (Manteniendo Engine)")
        lifecycleScope.coroutineContext.cancelChildren()
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null

        scraper?.destroyScraper()
        scraper = null
    }

    /**
     * Lógica asíncrona: Scraper -> URL Final -> ExoPlayer
     */
    private fun resolveLiveTokenAndPlay(baseUrl: String?) {
        if (baseUrl.isNullOrBlank()) return
        showLoading(true)
        Log.d("PlayerActivity", "Resolviendo Token: $baseUrl")
        
        scraper = InvisibleWebScraper(this)
        scraper?.fetchLiveStream(baseUrl) { urlFinal ->
            lifecycleScope.launch(Dispatchers.Main) {
                Log.d("PlayerActivity", "Token Capturado: $urlFinal")
                showLoading(false)
                setupExoPlayer(urlFinal)
            }
        }
    }

    private fun iniciarScraping(pageUrl: String) {
        showLoading(true)
        scraper = InvisibleWebScraper(this)
        scraper?.fetchLiveStream(pageUrl) { urlEncontrada ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (isLive) setupExoPlayer(urlEncontrada) else playWithVlc(urlEncontrada)
            }
        }
    }

    private fun playWithVlc(url: String) {
        showLoading(false)
        stopExoPlayer() // Asegurar limpieza del otro motor
        setupVLC(url)
    }

    private fun setupVLC(url: String) {
        val safeUrl = url.replace("https://", "http://")
        exoPlayerView.visibility = View.GONE
        vlcVideoLayout.visibility = View.VISIBLE
        
        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--http-user-agent=" + ApiClient.USER_AGENT)
        
        val referer = intent.getStringExtra("referer") ?: intent.getStringExtra("sourcePageUrl")
        if (!referer.isNullOrBlank()) {
            args.add("--http-referrer=$referer")
        }
        
        // Optimización Senior para MediaTek / API 22
        args.add("--http-reconnect")
        args.add("--network-caching=15000") // 15s Buffer
        args.add("--ipv4")                  // Evitar problemas de resolución IPv6
        args.add("--tcp-connection-timeout=30000")
        
        // Aceleración de Hardware (MediaCodec)
        args.add("--avcodec-hw=any")
        args.add("--drop-late-frames")
        args.add("--skip-frames")
        
        // REUTILIZACIÓN DE MOTOR: Solo crear si es nulo
        if (libVlc == null) {
            libVlc = LibVLC(this, args)
        }
        
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer?.attachViews(vlcVideoLayout, null, false, false)

        val media = Media(libVlc, Uri.parse(safeUrl))
        mediaPlayer?.media = media
        media.release()
        mediaPlayer?.play()
        
        updateVLCProgress()
    }

    private fun setupExoPlayer(url: String) {
        stopVlcPlayer() // Asegurar limpieza del otro motor
        vlcVideoLayout.visibility = View.GONE
        exoPlayerView.visibility = View.VISIBLE
        
        // SEGURIDAD SENIOR: Bypass SSL en Android 5.1
        val unsafeOkHttp = ApiClient.getUnsafeClient(followRedirects = true)
        val dataSourceFactory = OkHttpDataSource.Factory(unsafeOkHttp)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        exoPlayerView.player = exoPlayer
        
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .build()
            
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.playWhenReady = true
        
        exoPlayer?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("PlayerActivity", "ExoPlayer Error: ${error.message}")
                Toast.makeText(this@PlayerActivity, "Reintentando stream...", Toast.LENGTH_SHORT).show()
                resolveLiveTokenAndPlay(videoUrl)
            }
        })
    }

    private fun stopVlcPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun stopExoPlayer() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
            btnPlayPause.text = if (it.isPlaying) "PAUSA" else "REPRODUCIR"
        }
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
            btnPlayPause.text = if (it.isPlaying) "PAUSA" else "REPRODUCIR"
        }
    }

    private fun showTracksDialog() {
        // VLC Tracks (Legacy)
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isLive) {
                    mediaPlayer?.let {
                        val duration = it.length
                        val newPosition = (duration * progress / 100)
                        it.time = newPosition
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateVLCProgress() {
        lifecycleScope.launch(Dispatchers.Main) {
            while (mediaPlayer != null && !isFinishing) {
                val time = mediaPlayer?.time ?: 0
                val length = mediaPlayer?.length ?: 0
                if (length > 0) {
                    val progress = (time * 100 / length).toInt()
                    seekBar.progress = progress
                    tvCurrentTime.text = formatTime(time)
                    tvTotalTime.text = formatTime(length)
                }
                delay(1000)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toggleControls() {
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        controlsContainer.visibility = View.VISIBLE
        btnBack.visibility = View.VISIBLE
        isControlsVisible = true
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        hideControlsHandler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun hideControls() {
        controlsContainer.visibility = View.GONE
        btnBack.visibility = View.GONE
        isControlsVisible = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!isControlsVisible) {
                    showControls()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showControls()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showPaywall() {
        lifecycleScope.launch(Dispatchers.Main) {
            paywallContainer.visibility = View.VISIBLE
            stopAllPlayers()
            btnMercadoPago.isFocusable = true
            btnMercadoPago.requestFocus()
        }
    }

    private fun ejecutarCobroMercadoPago(emailUsuario: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiClient = ApiClient(this@PlayerActivity)
                val initPoint = apiClient.createMercadoPagoPreference(emailUsuario)

                withContext(Dispatchers.Main) {
                    if (!initPoint.isNullOrEmpty()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(initPoint))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@PlayerActivity, "Navegador no disponible.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun stopAllPlayers() {
        // SENIOR FIX: Liberación total de recursos para evitar bloqueos
        Log.d("PlayerActivity", "🛑 Liberación TOTAL de hardware")
        stopPlaybackButKeepEngine()
        
        libVlc?.release()
        libVlc = null
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        exoPlayer?.pause()
    }

    override fun onStop() {
        super.onStop()
        stopAllPlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllPlayers()
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }
}
