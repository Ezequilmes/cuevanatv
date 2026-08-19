package app.cuevanatv

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import app.cuevanatv.model.NewsItem
import com.bumptech.glide.Glide

class NewsCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            // Tamaño panorámico para noticias (aprox 16:9)
            setMainImageDimensions(440, 248) 
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val news = item as NewsItem
        val card = viewHolder.view as ImageCardView
        
        card.titleText = news.title
        card.contentLines = 1 // Evitar que el texto sea muy largo en la tarjeta
        
        Glide.with(card.context)
            .load(news.image_url)
            .centerCrop()
            .into(card.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImage = null
    }
}
