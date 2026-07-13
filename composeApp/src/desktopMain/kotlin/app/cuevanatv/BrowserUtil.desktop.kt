package app.cuevanatv

import java.awt.Desktop
import java.net.URI

actual fun openBrowser(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        println("Error abriendo navegador: ${e.message}")
    }
}
