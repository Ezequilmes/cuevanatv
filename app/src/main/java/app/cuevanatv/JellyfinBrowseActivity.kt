package app.cuevanatv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class JellyfinBrowseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)
        if (savedInstanceState == null) {
            val f = JellyfinBrowseFragment()
            f.arguments = intent.extras
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, f)
                .commit()
        }
    }
}

