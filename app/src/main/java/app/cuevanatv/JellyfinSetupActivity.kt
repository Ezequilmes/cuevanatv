package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.JellyfinClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JellyfinSetupActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (JellyfinPrefs.isConfigured(this) || !Auth.getToken(this).isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish(); return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Configurar Jellyfin"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val url = EditText(this).apply {
            hint = "URL (ej: http://192.168.0.67:8096)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val key = EditText(this).apply {
            hint = "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val btn = Button(this).apply {
            text = "Guardar y sincronizar"
            isAllCaps = false
        }
        val btnSupabase = Button(this).apply {
            text = "Usar cuenta (login)"
            isAllCaps = false
        }
        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        val info = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }

        root.addView(title)
        root.addView(url)
        root.addView(key)
        root.addView(btn)
        root.addView(btnSupabase)
        root.addView(progress)
        root.addView(info)
        setContentView(root)

        btnSupabase.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btn.setOnClickListener {
            val rawUrl = url.text?.toString()?.trim().orEmpty()
            val apiKey = key.text?.toString()?.trim().orEmpty()
            if (rawUrl.isEmpty() || apiKey.isEmpty()) {
                info.text = "Completa URL y API key"
                return@setOnClickListener
            }
            val normalizedUrl =
                if (rawUrl.startsWith("http://", true) || rawUrl.startsWith("https://", true)) rawUrl
                else "http://$rawUrl"

            progress.visibility = View.VISIBLE
            info.text = ""
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    JellyfinPrefs.save(this@JellyfinSetupActivity, normalizedUrl, apiKey)
                    try { JellyfinClient(this@JellyfinSetupActivity).getViews(1); true }
                    catch (_: Exception) { false }
                }
                progress.visibility = View.GONE
                if (ok) {
                    startActivity(Intent(this@JellyfinSetupActivity, MainActivity::class.java))
                    finish()
                } else {
                    info.text = "No se pudo conectar. Revisa URL, API key y firewall."
                }
            }
        }
    }
}

