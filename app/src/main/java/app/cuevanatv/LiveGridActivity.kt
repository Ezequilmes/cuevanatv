package app.cuevanatv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class LiveGridActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, LiveGridFragment())
                .commit()
        }
    }
}
