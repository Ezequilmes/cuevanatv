package app.cuevanatv

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cuevanatv.model.VideoItem
import app.cuevanatv.net.ApiClient
import app.cuevanatv.net.SupabaseClient
import app.cuevanatv.player.VideoPlayer
import app.cuevanatv.scraper.LinkResolver
import app.cuevanatv.scraper.HiddenStreamResolver
import coil3.compose.AsyncImage
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Paleta de colores Premium
private val BackgroundDark = Color(0xFF121212)
private val SurfaceDark = Color(0xFF1E1E1E)
private val AccentRed = Color(0xFFE50914)
private val TextPrimary = Color.White
private val TextSecondary = Color.LightGray

sealed class Screen {
    object Login : Screen()
    object Register : Screen()
    object Home : Screen()
    data class Details(val seriesId: String) : Screen()
    data class Player(val url: String) : Screen()
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val api = remember { ApiClient() }

    MaterialTheme(
        colors = darkColors(
            primary = AccentRed,
            background = BackgroundDark,
            surface = SurfaceDark,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Surface(color = BackgroundDark, modifier = Modifier.fillMaxSize()) {
            when (val screen = currentScreen) {
                is Screen.Login -> LoginScreen(
                    onLoginSuccess = { currentScreen = Screen.Home },
                    onGoToRegister = { currentScreen = Screen.Register },
                    api = api
                )
                is Screen.Register -> RegisterScreen(
                    onRegisterSuccess = { currentScreen = Screen.Login },
                    onBackToLogin = { currentScreen = Screen.Login },
                    api = api
                )
                is Screen.Home -> MainLayout(
                    onPlay = { currentScreen = Screen.Player(it) },
                    onNavigateToDetails = { currentScreen = Screen.Details(it) },
                    onLogout = { currentScreen = Screen.Login },
                    api = api
                )
                is Screen.Details -> SeriesDetailsScreen(
                    seriesId = screen.seriesId,
                    onPlay = { currentScreen = Screen.Player(it) },
                    onBack = { currentScreen = Screen.Home }
                )
                is Screen.Player -> VideoPlayer(
                    url = screen.url,
                    onBack = { currentScreen = Screen.Home }
                )
            }

            // Alerta Visual de Errores
            errorMessage?.let { error ->
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    title = { Text("Error de Reproducción", color = AccentRed) },
                    text = { Text(error, color = Color.White) },
                    confirmButton = {
                        Button(onClick = { errorMessage = null }) {
                            Text("Aceptar")
                        }
                    },
                    backgroundColor = SurfaceDark,
                    contentColor = Color.White
                )
            }
        }
    }
}

@Composable
expect fun ScrollableBox(
    modifier: Modifier,
    gridState: LazyGridState,
    content: @Composable () -> Unit
)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onGoToRegister: () -> Unit, api: ApiClient) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(350.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CuevanaTV", color = AccentRed, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = AccentRed)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = AccentRed)
            )
            
            error?.let {
                Text(it, color = AccentRed, modifier = Modifier.padding(top = 8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        val user = api.login(email, password)
                        isLoading = false
                        if (user != null && !user.has("error_type")) {
                            onLoginSuccess()
                        } else {
                            error = "Credenciales inválidas"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(backgroundColor = AccentRed)
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Iniciar Sesión", color = Color.White)
            }
            
            TextButton(onClick = onGoToRegister, modifier = Modifier.padding(top = 16.dp)) {
                Text("¿No tienes cuenta? Regístrate aquí", color = TextSecondary)
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBackToLogin: () -> Unit, api: ApiClient) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(350.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Registro", color = AccentRed, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electrónico") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = whatsapp,
                onValueChange = { whatsapp = it },
                label = { Text("WhatsApp (Opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            error?.let {
                Text(it, color = AccentRed, modifier = Modifier.padding(top = 8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    error = null
                    scope.launch {
                        val success = api.register(email, password, whatsapp)
                        isLoading = false
                        if (success) onRegisterSuccess()
                        else error = "Error al registrar"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                else Text("Registrarse")
            }
            
            TextButton(onClick = onBackToLogin) {
                Text("Volver al Login", color = TextSecondary)
            }
        }
    }
}

@Composable
fun MainLayout(onPlay: (String) -> Unit, onNavigateToDetails: (String) -> Unit, onLogout: () -> Unit, api: ApiClient) {
    var selectedCategory by remember { mutableStateOf<String?>("Películas") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Barra Lateral (NavigationRail personalizada)
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(SurfaceDark)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CuevanaTV", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(40.dp))
            
            CategoryItem("Películas", Icons.Default.PlayArrow, selectedCategory == "Películas") { selectedCategory = "Películas" }
            CategoryItem("Series", Icons.AutoMirrored.Filled.List, selectedCategory == "Series") { selectedCategory = "Series" }
            CategoryItem("Deportes", Icons.Default.Star, selectedCategory == "Eventos Deportivos") { selectedCategory = "Eventos Deportivos" }
            CategoryItem("TV en Vivo", Icons.Default.PlayArrow, selectedCategory == "Canales 24/7") { selectedCategory = "Canales 24/7" }
            
            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(24.dp))

            CategoryItem("Pagar Suscripción", Icons.Default.ShoppingCart, false) {
                openBrowser("https://www.mercadopago.com.ar/")
            }
            CategoryItem("Configuración", Icons.Default.Settings, false) { /* TODO */ }

            Spacer(modifier = Modifier.weight(1f))
            
            CategoryItem("Salir", Icons.AutoMirrored.Filled.ExitToApp, false) { onLogout() }
        }

        // Contenido Principal
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            HomeScreen(selectedCategory, onPlay, onNavigateToDetails, api)
        }
    }
}

@Composable
fun CategoryItem(text: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) AccentRed.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) AccentRed else TextSecondary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun HomeScreen(category: String?, onPlay: (String) -> Unit, onNavigateToDetails: (String) -> Unit, api: ApiClient) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorPopupMessage by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    // Estados para el Resolver Invisible
    var resolveUrl by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        isLoading = true
        // USANDO EL NUEVO SDK DE SUPABASE
        items = SupabaseClient.getFeed(category)
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentRed)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            ScrollableBox(
                modifier = Modifier.fillMaxSize(),
                gridState = gridState
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    state = gridState,
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items) { item ->
                        MovieCard(item, onClick = {
                            if (item.type == "series") {
                                onNavigateToDetails(item.id ?: "")
                            } else {
                                println("--- [DEBUG] Click en: ${item.title} ---")
                                val handler = CoroutineExceptionHandler { _, exception ->
                                    println("[UI] CoroutineExceptionHandler atrapó: $exception")
                                    exception.printStackTrace()
                                }
                                scope.launch(Dispatchers.IO + handler) {
                                    try {
                                        val details = api.getDetails(item.id ?: "")
                                        
                                        if (details != null) {
                                            var playable = details.playableUrl
                                            var source = details.sourcePageUrl

                                            // SOLUCIÓN 1: Si no hay URLs directas en 'titles', buscar en 'servers'
                                            if (playable.isNullOrEmpty() && source.isNullOrEmpty()) {
                                                println("[DEBUG] URLs nulas en 'titles', buscando en tabla 'servers'...")
                                                val servers = api.getServersForTitle(item.id ?: "")
                                                val firstServer = servers.firstOrNull()
                                                playable = firstServer?.playable_url
                                                source = firstServer?.page_url
                                            }

                                            println("[DEBUG] Enlaces finales -> Directo: $playable | Página: $source")

                                            // SOLUCIÓN 2: Bloqueo de URLs web en VLCJ (Resolver forzoso para PHP/HTML)
                                            val isWebUrl = playable?.let { 
                                                it.contains(".php") || it.contains(".html") || 
                                                (!it.endsWith(".m3u8") && !it.endsWith(".mp4") && !it.endsWith(".mkv"))
                                            } ?: false

                                            if (!playable.isNullOrEmpty() && !isWebUrl) {
                                                println("[DEBUG] Acción: Navegando a reproductor (URL directa)")
                                                withContext(Dispatchers.Main) { onPlay(playable) }
                                            } else if (!playable.isNullOrEmpty() && isWebUrl) {
                                                println("[DEBUG] Acción: URL Web detectada en playable_url. Redirigiendo a Resolver.")
                                                withContext(Dispatchers.Main) {
                                                    resolveUrl = playable
                                                    isResolving = true
                                                }
                                            } else if (!source.isNullOrEmpty()) {
                                                println("[DEBUG] Acción: Activando KCEF WebView Resolver (Source Page)")
                                                withContext(Dispatchers.Main) {
                                                    resolveUrl = source
                                                    isResolving = true
                                                }
                                            } else {
                                                println("[DEBUG] Acción: Fallo, todo es nulo.")
                                                withContext(Dispatchers.Main) {
                                                    errorPopupMessage = "Enlaces no encontrados en la base de datos."
                                                }
                                            }
                                        } else {
                                            println("[DEBUG] Acción: Fallo al obtener detalles (Null).")
                                            withContext(Dispatchers.Main) {
                                                errorPopupMessage = "Fallo al obtener detalles"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorPopupMessage = "Error: ${e.message}"
                                        }
                                    }
                                }
                            }
                        })
                    }
                }
            }

            // UI de Carga del Resolver
            if (isResolving) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentRed)
                        Spacer(Modifier.height(16.dp))
                        Text("Buscando enlace seguro...", color = Color.White)
                    }
                }
                
                // El componente WebView invisible
                HiddenStreamResolver(
                    url = resolveUrl ?: "",
                    onUrlFound = { m3u8 ->
                        isResolving = false
                        resolveUrl = null
                        onPlay(m3u8)
                    },
                    onTimeout = {
                        isResolving = false
                        resolveUrl = null
                        errorPopupMessage = "Fallo al extraer enlace (Timeout)"
                    }
                )
            }

            // Alertas
            errorPopupMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { errorPopupMessage = null },
                    title = { Text("Aviso", color = AccentRed) },
                    text = { Text(msg, color = Color.White) },
                    confirmButton = {
                        Button(onClick = { errorPopupMessage = null }) { Text("Aceptar") }
                    },
                    backgroundColor = SurfaceDark,
                    contentColor = Color.White
                )
            }
        }
    }
}

@Composable
fun HiddenStreamResolver(
    url: String,
    onUrlFound: (String) -> Unit,
    onTimeout: () -> Unit
) {
    // Chrome Desktop User-Agent real para saltar bloqueos
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    val state = rememberWebViewState(url)
    val navigator = rememberWebViewNavigator()
    var hasFound by remember { mutableStateOf(false) }

    // Configuración de KCEF/WebView
    state.webSettings.apply {
        isJavaScriptEnabled = true
        customUserAgentString = userAgent
    }

    // INTERCEPTOR DE RED PROACTIVO: Captura el .m3u8 en cuanto aparece en la navegación
    LaunchedEffect(state.lastLoadedUrl) {
        val currentUrl = state.lastLoadedUrl ?: ""
        if (currentUrl.contains(".m3u8") && !hasFound) {
            println("[RESOLVER] ¡Éxito! Enlace capturado proactivamente: $currentUrl")
            hasFound = true
            onUrlFound(currentUrl)
        }
    }

    // Timeout de seguridad (20 segundos)
    LaunchedEffect(url) {
        delay(20000)
        if (!hasFound) {
            println("[RESOLVER] Error: Tiempo de espera agotado para $url")
            onTimeout()
        }
    }

    // El WebView permanece invisible (contenedor de 1px)
    Box(modifier = Modifier.size(1.dp).clip(RoundedCornerShape(0.dp))) {
        WebView(
            state = state,
            navigator = navigator
        )
    }
}

@Composable
fun SeriesDetailsScreen(seriesId: String, onPlay: (String) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var episodes by remember { mutableStateOf<List<app.cuevanatv.net.EpisodeItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var resolveUrl by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seriesId) {
        try {
            isLoading = true
            println("[SERIES] Cargando episodios para ID: $seriesId")
            episodes = SupabaseClient.getEpisodes(seriesId)
            if (episodes.isEmpty()) {
                println("[SERIES] No se encontraron episodios.")
            }
        } catch (e: Exception) {
            println("[SERIES] Error al cargar: ${e.message}")
            errorMsg = "Error al cargar capítulos: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Temporadas y Capítulos", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                backgroundColor = SurfaceDark,
                elevation = 0.dp
            )
        },
        backgroundColor = BackgroundDark
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentRed)
                }
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg!!, color = Color.White)
                }
            } else if (episodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay capítulos disponibles", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    val grouped = episodes.groupBy { it.season ?: 1 }
                    grouped.forEach { (season, episodesInSeason) ->
                        item {
                            Text(
                                "Temporada $season",
                                color = AccentRed,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                            )
                        }
                        
                        items(episodesInSeason) { episode ->
                            EpisodeCard(episode) {
                                // Lógica de decisión idéntica a HomeScreen para seguridad
                                val playable = episode.playable_url
                                val source = episode.page_url
                                
                                val isWebUrl = playable?.let { 
                                    it.contains(".php") || it.contains(".html") || 
                                    (!it.endsWith(".m3u8") && !it.endsWith(".mp4") && !it.endsWith(".mkv"))
                                } ?: false

                                if (!playable.isNullOrEmpty() && !isWebUrl) {
                                    onPlay(playable)
                                } else if (!playable.isNullOrEmpty() && isWebUrl) {
                                    resolveUrl = playable
                                    isResolving = true
                                } else if (!source.isNullOrEmpty()) {
                                    resolveUrl = source
                                    isResolving = true
                                }
                            }
                        }
                    }
                }
            }

            if (isResolving) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentRed)
                        Spacer(Modifier.height(20.dp))
                        Text("Buscando señal del episodio...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                HiddenStreamResolver(
                    url = resolveUrl ?: "",
                    onUrlFound = { m3u8 ->
                        isResolving = false
                        onPlay(m3u8)
                    },
                    onTimeout = {
                        isResolving = false
                        errorMsg = "No se pudo obtener el video del capítulo"
                    }
                )
            }
        }
    }
}

@Composable
fun EpisodeCard(episode: app.cuevanatv.net.EpisodeItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        backgroundColor = SurfaceDark,
        shape = RoundedCornerShape(8.dp),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow, 
                contentDescription = null, 
                tint = AccentRed,
                modifier = Modifier.size(28.dp).background(Color.Black.copy(0.3f), RoundedCornerShape(14.dp)).padding(4.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Capítulo ${episode.episode}: ${episode.title ?: "Sin título"}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    episode.server_name ?: "Servidor Premium", 
                    color = TextSecondary, 
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

expect fun openBrowser(url: String)

@Composable
fun MovieCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = AccentRed),
                onClick = { 
                    println("[UI] Click en MovieCard disparado para: ${item.title}")
                    onClick() 
                }
            )
            .pointerHoverIcon(PointerIcon.Hand),
        elevation = 8.dp,
        backgroundColor = SurfaceDark
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!item.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.DarkGray, Color.Black))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = AccentRed.copy(0.5f), modifier = Modifier.size(48.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f))))
                    .padding(12.dp)
            ) {
                Text(
                    text = item.title ?: "Sin título",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
