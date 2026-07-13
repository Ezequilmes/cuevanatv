package app.cuevanatv.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.net.Uri

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val libVLC = remember { LibVLC(context) }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                mediaPlayer.attachViews(this, null, true, false)
                val media = Media(libVLC, Uri.parse(url))
                mediaPlayer.media = media
                media.release()
                mediaPlayer.play()
            }
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }
}
