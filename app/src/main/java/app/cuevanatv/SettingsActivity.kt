package app.cuevanatv

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SettingsActivity : FragmentActivity() {

    private lateinit var tvUserEmail: TextView
    private lateinit var tvExpiryDate: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvUserStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Inicialización SEGURA de vistas
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvExpiryDate = findViewById(R.id.tvExpiryDate)
        tvVersion = findViewById(R.id.tvVersion)
        tvUserStatus = findViewById(R.id.tvUserStatus)

        setupUI()
        loadProfileData()
    }

    private fun setupUI() {
        tvVersion.text = "Versión: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // FILA 1
        findViewById<CardView>(R.id.card_profile).setOnClickListener {
            mostrarDialogoCerrarSesion()
        }

        findViewById<CardView>(R.id.card_payment_status).setOnClickListener {
            // Lógica para Mercado Pago o info de pago
            Toast.makeText(this, "Abriendo estado de pago...", Toast.LENGTH_SHORT).show()
        }

        findViewById<CardView>(R.id.card_support).setOnClickListener {
            abrirSoporteWhatsApp()
        }

        // FILA 2
        findViewById<CardView>(R.id.btnCheckUpdates).setOnClickListener {
            verificarActualizacionManual()
        }

        findViewById<CardView>(R.id.card_player_choice).setOnClickListener {
            Toast.makeText(this, "Seleccionando reproductor...", Toast.LENGTH_SHORT).show()
        }

        findViewById<CardView>(R.id.card_streaming_quality).setOnClickListener {
            Toast.makeText(this, "Configurando calidad...", Toast.LENGTH_SHORT).show()
        }

        // FILA 3
        findViewById<CardView>(R.id.card_clean_cache).setOnClickListener {
            ejecutarLimpiezaCache()
        }

        findViewById<CardView>(R.id.card_parental_control).setOnClickListener {
            Toast.makeText(this, "Control Parental", Toast.LENGTH_SHORT).show()
        }

        findViewById<CardView>(R.id.card_speed_test).setOnClickListener {
            Toast.makeText(this, "Iniciando test de velocidad...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfileData() {
        val token = Auth.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                val status = ApiClient(this@SettingsActivity).checkStatus(token)
                val email = status?.optString("email", "Usuario")
                tvUserEmail.text = email
                
                val isBypass = status?.optBoolean("bypass_qr", false) ?: false
                if (isBypass) {
                    tvUserStatus.text = "Plan VIP Ilimitado"
                    tvExpiryDate.text = "Vencimiento: Ilimitado"
                } else {
                    val fechaRaw = if (status != null && !status.isNull("fecha_vencimiento")) {
                        status.optString("fecha_vencimiento")
                    } else null
                    tvExpiryDate.text = "Vence: ${formatDateSafely(fechaRaw)}"
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error loading profile", e)
                tvUserEmail.text = "Error de conexión"
            }
        }
    }

    private fun formatDateSafely(dateStr: String?): String {
        if (dateStr.isNullOrBlank() || dateStr == "null") return "No definida"
        return try {
            val cleanDate = dateStr.replace("Z", "").split("+")[0].split(".")[0]
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(cleanDate)
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            date?.let { outputFormat.format(it) } ?: "No definida"
        } catch (e: Exception) {
            "No definida"
        }
    }

    private fun ejecutarLimpiezaCache() {
        try {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "Caché de CuevanaTV liberada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al borrar caché", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDialogoCerrarSesion() {
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Estás seguro que deseas salir?")
            .setPositiveButton("Salir") { _, _ ->
                Auth.clear(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirSoporteWhatsApp() {
        try {
            val url = "https://wa.me/5491132664036"
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Instalá WhatsApp o abrí el sitio de soporte", Toast.LENGTH_LONG).show()
        }
    }

    private fun verificarActualizacionManual() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val credentials = "rapidseedbox93266:ce2d2477e8f1"
                val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                
                val request = Request.Builder()
                    .url("https://rapidseedbox93266.basic-002.seedbox.vip/apk/version.json")
                    .addHeader("Authorization", "Basic $base64Credentials")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@SettingsActivity, "Error al conectar con servidor", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val json = JSONObject(response.body?.string() ?: "")
                    val serverVersionCode = json.optInt("versionCode", 0)
                    val downloadUrl = json.optString("downloadUrl", "")
                    val changelog = json.optString("changelog", "Mejoras de rendimiento.")

                    withContext(Dispatchers.Main) {
                        if (serverVersionCode > BuildConfig.VERSION_CODE) {
                            AlertDialog.Builder(this@SettingsActivity, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                                .setTitle("Actualización Encontrada")
                                .setMessage("Cambios:\n$changelog")
                                .setPositiveButton("Actualizar Ahora") { _, _ ->
                                    iniciarDescarga(downloadUrl)
                                }
                                .setNegativeButton("Más Tarde", null)
                                .show()
                        } else {
                            Toast.makeText(this@SettingsActivity, "La aplicación está actualizada", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun iniciarDescarga(url: String) {
        try {
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo
            val authHeader = userInfo?.let { "Basic " + Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
            
            val cleanUrl = if (userInfo != null) {
                val host = uri.host ?: ""
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val scheme = uri.scheme ?: "https"
                val path = uri.path ?: ""
                val query = uri.query?.let { "?$it" } ?: ""
                "$scheme://$host$port$path$query"
            } else url

            val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-release.apk")
            if (destination.exists()) destination.delete()

            val request = DownloadManager.Request(Uri.parse(cleanUrl))
                .setTitle("Actualizando CuevanaTV")
                .setDescription("Descargando versión estable...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(destination))
            
            authHeader?.let { request.addRequestHeader("Authorization", it) }

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = manager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId == id) {
                        context.unregisterReceiver(this)
                        instalarApk(destination)
                    }
                }
            }
            
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(onComplete, filter)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fallo al iniciar descarga", Toast.LENGTH_SHORT).show()
        }
    }

    private fun instalarApk(file: File) {
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("InstallError", "Fallo al instalar: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Error al abrir el instalador del sistema", Toast.LENGTH_LONG).show()
            }
        }
    }
}
