package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.cuevanatv.net.ApiClient
import kotlinx.coroutines.launch

class LoginActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Auth.getToken(this).isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Iniciar sesión"
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val email = EditText(this).apply {
            hint = "Email"
        }
        val pass = EditText(this).apply {
            hint = "Contraseña"
            setSingleLine()
        }
        val btn = Button(this).apply {
            text = "Entrar"
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
        root.addView(email)
        root.addView(pass)
        root.addView(btn)
        root.addView(progress)
        root.addView(info)
        setContentView(root)
        btn.setOnClickListener {
            val e = email.text?.toString()?.trim().orEmpty()
            val p = pass.text?.toString()?.trim().orEmpty()
            if (e.isEmpty() || p.isEmpty()) {
                info.text = "Completa email y contraseña"
                return@setOnClickListener
            }
            progress.visibility = View.VISIBLE
            lifecycleScope.launch {
                val token = ApiClient(this@LoginActivity).login(e, p)
                progress.visibility = View.GONE
                if (token.isNullOrEmpty()) {
                    info.text = "Login fallido. Verifica credenciales."
                } else {
                    Auth.saveToken(this@LoginActivity, token)
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}
