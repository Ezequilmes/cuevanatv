package app.cuevanatv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class JellyfinSearchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        if (savedInstanceState == null) {
            val f = JellyfinSearchFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, f)
                .commit()
        }
    }
}

