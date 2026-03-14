package app.cuevanatv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

class DetailsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        if (savedInstanceState == null) {
            val f = MovieDetailsFragment()
            f.arguments = intent.extras
            supportFragmentManager.beginTransaction()
                .replace(R.id.details_container, f)
                .commit()
        }
    }
}

