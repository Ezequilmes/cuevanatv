package app.cuevanatv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ACTIVIDAD DE REGISTRO (Senior Optimized)
 * Implementa Auto-Login inmediato y enrutamiento directo a MainActivity
 * para eliminar la fricción en el onboarding de nuevos usuarios.
 */
class RegisterActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 40, 60, 40)
        }

        val title = TextView(this).apply {
            text = "CREAR CUENTA NUEVA"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }

        val emailField = EditText(this).apply {
            id = View.generateViewId()
            hint = "Email / Usuario"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            background = roundedRect("#222222")
            setPadding(40, 30, 40, 30)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val passField = EditText(this).apply {
            id = View.generateViewId()
            hint = "Contraseña"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedRect("#222222")
            setPadding(40, 30, 40, 30)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val whatsappField = LinearLayout(this).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect("#222222")
            setPadding(40, 0, 40, 0)
            
            val prefix = TextView(context).apply {
                text = "+549"
                setTextColor(Color.LTGRAY)
                textSize = 16f
            }
            val input = EditText(context).apply {
                id = View.generateViewId()
                hint = "WhatsApp"
                setHintTextColor(Color.GRAY)
                setTextColor(Color.WHITE)
                inputType = android.text.InputType.TYPE_CLASS_PHONE
                background = null
                setPadding(20, 30, 20, 30)
                tag = "phone_input"
            }
            addView(prefix)
            addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val btnRegister = Button(this).apply {
            text = "REGISTRARME Y COMENZAR PRUEBA"
            setTextColor(Color.WHITE)
            background = roundedRect("#E50914")
            setPadding(0, 30, 0, 30)
            isAllCaps = true
            isFocusable = true
        }

        val info = TextView(this).apply {
            setTextColor(Color.parseColor("#FFCC00"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
            visibility = View.GONE
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        root.addView(title)
        root.addView(emailField, lpWithMargins(0, 20))
        root.addView(passField, lpWithMargins(0, 20))
        root.addView(whatsappField, lpWithMargins(0, 40))
        root.addView(btnRegister, lpWithMargins(0, 40))
        root.addView(progress)
        root.addView(info)

        scrollView.addView(root)
        setContentView(scrollView)

        btnRegister.setOnClickListener {
            val e = emailField.text?.toString()?.trim().orEmpty()
            val p = passField.text?.toString()?.trim().orEmpty()
            val w = whatsappField.findViewWithTag<EditText>("phone_input").text?.toString()?.trim().orEmpty()

            if (e.isEmpty() || p.isEmpty() || w.isEmpty()) {
                info.text = "Completa todos los campos"
                info.visibility = View.VISIBLE
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            info.visibility = View.GONE
            
            lifecycleScope.launch {
                val api = ApiClient(this@RegisterActivity)
                
                // 1. Registro directo en Supabase
                val success = api.register(e, p, "+549$w")
                
                if (success) {
                    // 2. AUTO-LOGIN INMEDIATO (Fricción Cero)
                    val user = api.login(e, p)
                    
                    if (user != null && !user.has("error_type")) {
                        val userId = user.optString("id", "")
                        
                        if (userId.isNotEmpty() && userId != "null") {
                            // 3. Verificación y Registro de Dispositivo (Importante para TV)
                            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                            val model = Build.MODEL
                            val limit = user.optInt("limite_pantallas", 1)
                            
                            val devices = api.getUserDevices(userId)
                            val isKnownDevice = devices.any { it.optString("device_id") == deviceId }

                            if (!isKnownDevice) {
                                if (devices.size >= limit) {
                                    progress.visibility = View.GONE
                                    withContext(Dispatchers.Main) {
                                        AlertDialog.Builder(this@RegisterActivity)
                                            .setTitle("Límite de Pantallas")
                                            .setMessage("Has alcanzado el límite de $limit dispositivos. Inicia sesión en otro equipo.")
                                            .setPositiveButton("OK") { _, _ ->
                                                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                                finish()
                                            }
                                            .show()
                                    }
                                    return@launch
                                } else {
                                    api.registerDevice(userId, deviceId, model)
                                }
                            }

                            // 4. Guardado de Sesión y Enrutamiento Directo a MainActivity
                            Auth.saveToken(this@RegisterActivity, userId, e, userId)
                            val isBypass = user.optBoolean("bypass_qr", false)
                            val isActive = user.optBoolean("active", true) || isBypass // Forzamos true en registro
                            val expiry = user.optString("fecha_vencimiento", "")
                            Auth.saveUserStatus(this@RegisterActivity, isActive, isBypass, expiry)

                            progress.visibility = View.GONE
                            val intent = Intent(this@RegisterActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // Fallback si no hay ID de usuario
                            goToLoginWithToast(progress)
                        }
                    } else {
                        // Fallback: Si el auto-login falla, enviamos al login manual con mensaje
                        goToLoginWithToast(progress)
                    }
                } else {
                    progress.visibility = View.GONE
                    info.text = "Error al registrar. Intenta con otro email."
                    info.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun goToLoginWithToast(progress: View) {
        progress.visibility = View.GONE
        Toast.makeText(this@RegisterActivity, "Registro exitoso, por favor inicia sesión", Toast.LENGTH_LONG).show()
        val intent = Intent(this@RegisterActivity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun roundedRect(color: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = 15f
        }
    }

    private fun lpWithMargins(top: Int, bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, top, 0, bottom)
        }
    }
}
