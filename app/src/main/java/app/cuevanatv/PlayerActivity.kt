package app.cuevanatv

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.StyledPlayerView
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.scraper.LinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View

class PlayerActivity : FragmentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        val rawUrl = intent.getStringExtra("url") ?: intent.getStringExtra("pageUrl")
        if (rawUrl == null) {
            finish(); return
        }
        val playerView = findViewById<StyledPlayerView>(R.id.player_view)
        val loading = findViewById<View>(R.id.loading_overlay)
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        if (isPlayable(rawUrl)) {
            val mediaItem = MediaItem.fromUri(Uri.parse(rawUrl))
            player?.setMediaItem(mediaItem)
        } else {
            loading.visibility = View.VISIBLE
            lifecycleScope.launch {
                val resolved = withContext(Dispatchers.IO) { LinkResolver().resolvePlayableUrl(rawUrl) }
                if (resolved != null) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(resolved))
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                    player?.playWhenReady = true
                } else {
                    finish()
                }
                loading.visibility = View.GONE
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    private fun isPlayable(url: String): Boolean {
        val u = url.lowercase()
        return u.endsWith(".m3u8") || u.endsWith(".mp4") || u.contains(".m3u8")
    }
}
