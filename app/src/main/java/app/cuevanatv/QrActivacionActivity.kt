package app.cuevanatv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ACTIVIDAD DE ACTIVACIÓN POR QR (Senior Optimized)
 * Flujo: Obtener Preferencia -> Generar QR -> Polling de Estado -> Entrada Automática
 */
class QrActivacionActivity : FragmentActivity() {

    private lateinit var apiClient: ApiClient
    private lateinit var ivQr: ImageView
    private lateinit var tvStatus: TextView
    private var pollingJob: Job? = null
    private val TAG = "QR_ACTIVACION"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_activacion)

        apiClient = ApiClient(this)
        ivQr = findViewById(R.id.ivQr)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnBackToLogin).setOnClickListener {
            irALogin()
        }

        iniciarFlujoPago()
    }

    private fun iniciarFlujoPago() {
        val email = Auth.getEmail(this)
        if (email.isEmpty() || email == "invitado@cuevana.tv") {
            tvStatus.text = "Error: Inicia sesión nuevamente"
            return
        }

        lifecycleScope.launch {
            try {
                tvStatus.text = "Generando orden de pago..."
                val initPoint = apiClient.createMercadoPagoPreference(email)
                
                if (!initPoint.isNullOrEmpty()) {
                    val qrBitmap = generateQrCode(initPoint)
                    if (qrBitmap != null) {
                        ivQr.setImageBitmap(qrBitmap)
                        tvStatus.text = "Escanea el QR para activar tu cuenta"
                        startPolling()
                    } else {
                        tvStatus.text = "Error al generar código QR"
                    }
                } else {
                    tvStatus.text = "Error al conectar con Mercado Pago"
                    Toast.makeText(this@QrActivacionActivity, "Reintenta en unos momentos", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error flujo pago: ${e.message}")
                tvStatus.text = "Error de red"
            }
        }
    }

    private fun generateQrCode(content: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val color = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                    bitmap.setPixel(x, y, color)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error QR: ${e.message}")
            null
        }
    }

    private fun startPolling() {
        val userId = Auth.getUserId(this)
        if (userId.isEmpty()) {
            Log.e(TAG, "No UserID found for polling")
            return
        }

        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (true) {
                delay(5000) // Polling cada 5 segundos
                Log.d(TAG, "Verificando estado de activación para: $userId")
                val status = apiClient.checkStatus(userId)
                
                if (status != null && status.optBoolean("active", false)) {
                    Log.d(TAG, "¡CUENTA ACTIVADA!")
                    Auth.saveUserStatus(this@QrActivacionActivity, true, status.optBoolean("bypass_qr", false))
                    Toast.makeText(this@QrActivacionActivity, "¡Cuenta Activada!", Toast.LENGTH_LONG).show()
                    irAMain()
                    break
                }
            }
        }
    }

    private fun irAMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun irALogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        super.onDestroy()
    }

    override fun onBackPressed() {
        irALogin()
    }
}
