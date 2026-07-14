package app.cuevanatv

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class TorrServerManager(private val context: Context) {
    private var process: Process? = null

    fun startServer(): Boolean {
        try {
            val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            val binFile = File(nativeLibraryDir, "libtorrserver.so")

            if (!binFile.exists()) {
                Log.e("TorrServer", "Binario nativo no encontrado en $nativeLibraryDir. ABIs=${Build.SUPPORTED_ABIS.joinToString()}")
                return false
            }

            binFile.setExecutable(true)

            // CORRECCIÓN: Usamos el puerto 8093 para evitar colisiones con el sistema Philips
            if (isServerRunning()) {
                Log.d("TorrServer_Process", "El servidor ya estaba corriendo en el puerto 8093.")
                return true
            }

            // CORRECCIÓN 2: Argumentos largos para mayor seguridad en TorrServer MatrEx
            val pb = ProcessBuilder(binFile.absolutePath, "--port", "8093", "--path", context.filesDir.absolutePath)
            pb.directory(context.filesDir)
            
            pb.environment()["GOMAXPROCS"] = "1" 
            pb.redirectErrorStream(true)
            
            process = pb.start()
            Log.d("TorrServer_Process", "Proceso binario ejecutado en puerto 8093. Escuchando logs...")

            thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.w("TorrServer_Process", "-> $line")
                    }
                } catch (e: Exception) {
                    Log.e("TorrServer_Process", "Error leyendo logs: ${e.message}")
                }
            }

            Thread.sleep(3000)

            val running = isServerRunning()
            if (running) {
                Log.d("TorrServer_Process", "Servidor confirmado en 8093 (Abierto a la red local)")
            } else {
                Log.e("TorrServer_Process", "El servidor NO responde en puerto 8093 después de 3s")
            }
            return running
        } catch (e: Exception) {
            Log.e("TorrServer", "Error al iniciar: ${e.message}")
            return false
        }
    }

    fun stopServer() {
        process?.destroy()
        process = null
    }

    private fun isServerRunning(): Boolean {
        val client = OkHttpClient.Builder()
            .proxy(java.net.Proxy.NO_PROXY) // CORRECCIÓN 1: Evadir el proxy de la TV para el ping de comprobación
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
        
        val request127 = Request.Builder().url("http://127.0.0.1:8093/echo").build()
        val run127 = try {
            client.newCall(request127).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
        
        if (run127) return true
        
        val requestLocal = Request.Builder().url("http://localhost:8093/echo").build()
        return try {
            client.newCall(requestLocal).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }
}
