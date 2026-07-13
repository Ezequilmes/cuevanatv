package app.cuevanatv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.awt.Component
import java.awt.Frame

@Composable
fun VlcjPlayer(
    url: String,
    modifier: Modifier = Modifier,
    onMediaEnd: () -> Unit = {}
) {
    val mediaPlayerComponent = remember {
        EmbeddedMediaPlayerComponent()
    }

    DisposableEffect(url) {
        mediaPlayerComponent.mediaPlayer().media().play(url)
        
        onDispose {
            mediaPlayerComponent.release()
        }
    }

    SwingPanel(
        background = Color.Black,
        modifier = modifier,
        factory = {
            mediaPlayerComponent
        }
    )
}
