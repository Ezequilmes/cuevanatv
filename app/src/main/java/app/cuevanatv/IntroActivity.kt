package app.cuevanatv

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.FragmentActivity

class IntroActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_intro)
        val btn = findViewById<Button>(R.id.btn_continue)
        btn.setOnClickListener {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
