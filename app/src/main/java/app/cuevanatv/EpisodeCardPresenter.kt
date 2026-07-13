package app.cuevanatv

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.widget.Toast
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import app.cuevanatv.model.ServerItem
import com.bumptech.glide.Glide

class EpisodeCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setMainImageDimensions(320, 180) // Formato 16:9 para episodios
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val ep = item as ServerItem
        val card = viewHolder.view as ImageCardView
        
        card.titleText = "Capítulo ${ep.episode_number}"
        card.contentText = ep.name ?: "Reproducir"
        card.setMainImage(ColorDrawable(Color.DKGRAY))

        card.setOnClickListener {
            val intent = Intent(it.context, PlayerActivity::class.java).apply {
                putExtra("video_url", ep.playable_url)
                putExtra("title", "Capítulo ${ep.episode_number}: ${ep.name}")
                putExtra("is_live", false)
                ep.referer?.let { ref -> putExtra("referer", it) }
            }
            it.context.startActivity(intent)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImage = null
    }
}
