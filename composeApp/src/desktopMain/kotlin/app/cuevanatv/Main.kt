package app.cuevanatv

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File

fun main() {
    // 1. Intentar configuración manual de bundle (vlc-resources)
    setupVlcNatives()
    
    // 2. Intentar descubrimiento automático como respaldo
    try {
        uk.co.caprica.vlcj.factory.discovery.NativeDiscovery().discover()
    } catch (e: Exception) {
        println("Aviso: Falló NativeDiscovery, confiando en setupVlcNatives.")
    }
    
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "CuevanaTV Desktop",
        ) {
            App()
        }
    }
}

private fun setupVlcNatives() {
    val os = System.getProperty("os.name").lowercase()
    if (os.contains("win")) {
        // La ruta de recursos en el .exe instalado es relativa al ejecutable
        val appPath = System.getProperty("compose.application.resources.dir")?.let { File(it) }
        val vlcPath = if (appPath != null && appPath.exists()) {
            File(appPath, "vlc-win64").absolutePath
        } else {
            // Ruta de desarrollo (para correr desde Android Studio)
            File("vlc-resources/vlc-win64").absolutePath
        }

        println("VLC Native Path: $vlcPath")
        
        // Decirle a JNA dónde buscar las DLLs
        System.setProperty("jna.library.path", vlcPath)
        
        // Opcional: plugins de VLC
        System.setProperty("vlc.plugin.path", "$vlcPath\\plugins")
    }
}
