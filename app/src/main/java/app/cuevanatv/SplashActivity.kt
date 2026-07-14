package app.cuevanatv

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.*

class SplashActivity : FragmentActivity() {

    private var videoView: VideoView? = null
    private var initJob: Job? = null
    private var nextIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        videoView = findViewById(R.id.video_intro)
        setupVideoView()
        ejecutarInicializacion()

        // Temporizador de seguridad global: Si a los 10 segundos no ha pasado nada, salimos.
        lifecycleScope.launch {
            delay(10000)
            if (!isFinishing) {
                Log.w("Splash", "Activando fallback global de 10 segundos")
                finalizarSplash()
            }
        }
    }

    private fun setupVideoView() {
        try {
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.intro}")
            videoView?.setVideoURI(videoUri)
            
            videoView?.setOnPreparedListener { mp ->
                mp.isLooping = false
                try {
                    videoView?.start()
                    // Si empieza a reproducir, cancelamos el timeout corto
                } catch (e: Exception) {
                    finalizarSplash()
                }
            }

            videoView?.setOnCompletionListener {
                finalizarSplash()
            }

            videoView?.setOnErrorListener { _, _, _ ->
                Log.e("Splash", "Error en VideoView, saltando intro...")
                finalizarSplash()
                true
            }

            // Si a los 3 segundos el video no ha preparado nada, saltamos.
            // Esto previene el hang negro en Philips/Sony TVs con bugs en MediaCodec
            lifecycleScope.launch {
                delay(3500)
                if (videoView?.isPlaying == false) {
                    Log.w("Splash", "VideoView no inició en 3.5s, forzando salida")
                    finalizarSplash()
                }
            }

        } catch (e: Exception) {
            finalizarSplash()
        }
    }

    private fun ejecutarInicializacion() {
        initJob = lifecycleScope.launch {
            // 1. Verificar Internet
            if (!isNetworkAvailable()) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Sin Conexión", "No se detectó una conexión a internet activa.")
                }
                return@launch
            }

            // 2. Tareas de inicio en paralelo
            try {
                val p2pTask = async(Dispatchers.IO) {
                    try {
                        val serverManager = TorrServerManager(this@SplashActivity)
                        serverManager.startServer()
                    } catch (e: Exception) {
                        Log.e("Splash", "Error al iniciar TorrServer: ${e.message}")
                    }
                }

                val authTask = async(Dispatchers.IO) {
                    val token = Auth.getToken(this@SplashActivity)
                    if (!token.isNullOrEmpty()) {
                        try {
                            ApiClient(this@SplashActivity).checkStatus(token)
                        } catch (e: Exception) { null }
                    } else null
                }

                p2pTask.await()
                val userStatus = authTask.await()

                // Preparamos el destino
                nextIntent = if (userStatus != null) {
                    Intent(this@SplashActivity, MainActivity::class.java)
                } else {
                    Intent(this@SplashActivity, LoginActivity::class.java)
                }
                
            } catch (e: Exception) {
                Log.e("Splash", "Error en carga: ${e.message}")
                nextIntent = Intent(this@SplashActivity, LoginActivity::class.java)
            }
        }
    }

    private fun finalizarSplash() {
        lifecycleScope.launch {
            try {
                // Esperamos un máximo de 5 segundos para las tareas de fondo
                // Si tardan más, continuamos para no dejar al usuario en una pantalla negra
                withTimeout(5000) {
                    initJob?.join()
                }
            } catch (e: Exception) {
                Log.w("Splash", "Timeout o error esperando inicialización: ${e.message}")
            }
            
            val intent = nextIntent ?: Intent(this@SplashActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // Requisito: No se debe poder saltar el video con el control remoto
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Bloqueamos cualquier interacción para asegurar que vean la intro
        return true
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val nwInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return nwInfo.isConnected
        }
    }

    private fun showErrorDialog(titulo: String, mensaje: String) {
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(titulo)
            .setMessage(mensaje)
            .setCancelable(false)
            .setPositiveButton("Reintentar") { _, _ -> ejecutarInicializacion() }
            .setNegativeButton("Salir") { _, _ -> finish() }
            .show()
    }
}
