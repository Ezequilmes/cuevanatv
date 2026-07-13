package app.cuevanatv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun VideoPlayer(
    url: String,
    modifier: Modifier,
    onBack: () -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VlcjPlayer(
            url = url,
            modifier = Modifier.fillMaxSize()
        )
        
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Text("Volver")
        }
    }
}
