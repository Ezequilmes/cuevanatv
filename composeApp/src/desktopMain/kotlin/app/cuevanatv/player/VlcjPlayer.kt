package app.cuevanatv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VlcjPlayer(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaEnd: () -> Unit = {}
) {
    // Factory con argumentos para estabilidad y hardware
    val factory = remember { 
        MediaPlayerFactory(
            "--avcodec-hw=none",      // Desactivar HW para evitar fallos D3D11
            "--no-video-title-show",  // No mostrar título sobre el video
            "--quiet"                 // Menos ruido en consola
        ) 
    }
    val mediaPlayer = remember { factory.mediaPlayers().newEmbeddedMediaPlayer() }
    
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    // Auto-ocultar controles
    LaunchedEffect(showControls) {
        if (showControls) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
            factory.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        showControls = true
                    }
                }
            }
    ) {
        SwingPanel(
            background = Color.Black,
            modifier = Modifier.fillMaxSize(),
            factory = {
                Canvas().apply {
                    background = java.awt.Color.BLACK
                    addHierarchyListener(object : HierarchyListener {
                        override fun hierarchyChanged(e: HierarchyEvent) {
                            val isDisplayableChanged = (e.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L
                            if (isDisplayableChanged && isDisplayable) {
                                mediaPlayer.videoSurface().set(factory.videoSurfaces().newVideoSurface(this@apply))
                                val options = arrayOf(
                                    ":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
                                    ":http-referrer=https://streamtpday1.xyz/"
                                )
                                mediaPlayer.media().play(url, *options)
                                removeHierarchyListener(this)
                            }
                        }
                    })
                }
            }
        )

        // Capa de Controles Superpuesta
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Botón Volver
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                }

                // Botón Play/Pausa Central
                Surface(
                    onClick = {
                        if (mediaPlayer.status().isPlaying) {
                            mediaPlayer.controls().pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.controls().play()
                            isPlaying = true
                        }
                    },
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Center).size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }
    }
}
