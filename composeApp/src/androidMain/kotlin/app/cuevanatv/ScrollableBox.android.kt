package app.cuevanatv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ScrollableBox(
    modifier: Modifier,
    gridState: LazyGridState,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}
