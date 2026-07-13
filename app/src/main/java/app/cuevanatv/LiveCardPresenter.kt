package app.cuevanatv

import android.graphics.Color
import android.view.ViewGroup
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import app.cuevanatv.model.VideoItem
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.net.URLEncoder

class LiveCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            // Tamaño 16:9 optimizado para 3 columnas
            setMainImageDimensions(480, 270)
            cardType = BaseCardView.CARD_TYPE_INFO_UNDER
            infoVisibility = BaseCardView.CARD_REGION_VISIBLE_ALWAYS
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as VideoItem
        val card = viewHolder.view as ImageCardView
        
        card.titleText = video.title ?: "Canal TV"
        card.contentText = "🔴 Señal en Vivo"

        // Generamos logo dinámico si no hay imagen (Placeholder Inteligente)
        val cleanTitle = video.title ?: "TV"
        val avatarUrl = "https://ui-avatars.com/api/?name=${URLEncoder.encode(cleanTitle, "UTF-8")}&background=E50914&color=fff&size=512&font-size=0.35&bold=true"

        Glide.with(card.context)
            .load(video.imageUrl)
            .apply(RequestOptions().centerCrop())
            .error(Glide.with(card.context).load(avatarUrl))
            .fallback(Glide.with(card.context).load(avatarUrl))
            .into(card.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        Glide.with(card.context).clear(card.mainImageView)
    }
}
