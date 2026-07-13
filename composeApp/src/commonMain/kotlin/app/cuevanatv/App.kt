package app.cuevanatv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.ApiClient
import app.cuevanatv.player.VideoPlayer
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    data class Player(val url: String) : Screen()
}

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        val scaffoldState = rememberScaffoldState()
        
        Scaffold(
            scaffoldState = scaffoldState,
            topBar = {
                TopAppBar(title = { Text("CuevanaTV Desktop") })
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(onPlay = { url ->
                            currentScreen = Screen.Player(url)
                        })
                    }
                    is Screen.Player -> {
                        VideoPlayer(
                            url = screen.url,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                }
            }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("CuevanaTV") })
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (val screen = currentScreen) {
                    is Screen.Home -> HomeScreen(onPlay = { currentScreen = Screen.Player(it) })
                    is Screen.Player -> VideoPlayer(url = screen.url, onBack = { currentScreen = Screen.Home })
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onPlay: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }
    var items by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        items = api.getFeed(null)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                MovieCard(item, onClick = {
                    scope.launch {
                        val details = api.getDetails(null, item.id ?: "")
                        val playable = details.playableUrl ?: details.servers.firstOrNull()?.playable_url
                        if (playable != null) {
                            onPlay(playable)
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun MovieCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clickable(onClick = onClick),
        elevation = 4.dp
    ) {
        Column {
            // Here we should use a cross-platform Image loader like Coil or Kamel
            // For now, just show the title
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(item.title ?: "Sin título", style = MaterialTheme.typography.h6)
            }
            Text(
                text = item.title ?: "",
                modifier = Modifier.padding(8.dp),
                maxLines = 2
            )
        }
    }
}
