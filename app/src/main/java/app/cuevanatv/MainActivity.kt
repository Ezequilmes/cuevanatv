package app.cuevanatv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Auth.getToken(this).isNullOrEmpty()) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish(); return
        }
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container_view, MainBrowseFragment())
                .commit()
        }
    }
}
