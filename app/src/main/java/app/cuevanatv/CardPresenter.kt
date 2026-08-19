package app.cuevanatv

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import app.cuevanatv.model.VideoItem
import com.bumptech.glide.Glide

class CardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context)
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.setMainImageDimensions(300, 450)
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val video = item as VideoItem
        val card = viewHolder.view as ImageCardView
        card.titleText = video.title
        card.setMainImage(ColorDrawable(Color.DKGRAY))
        
        Glide.with(card.context)
            .load(video.imageUrl)
            .centerCrop()
            .into(card.mainImageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImage = null
    }
}
