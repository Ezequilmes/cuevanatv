package app.cuevanatv

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import app.cuevanatv.model.ServerItem
import android.content.Intent

class EpisodeCardPresenter(private val episodes: List<ServerItem> = emptyList()) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter_card, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val ep = item as ServerItem
        val view = viewHolder.view
        
        val tvNumber = view.findViewById<TextView>(R.id.chapter_number)
        val tvTitle = view.findViewById<TextView>(R.id.chapter_title)
        val tvSubtitle = view.findViewById<TextView>(R.id.chapter_subtitle)

        tvNumber.text = String.format("%02d", ep.episode_number ?: 0)
        tvTitle.text = ep.name ?: "Capítulo"
        tvSubtitle.text = "REPRODUCIR AHORA"

        view.setOnClickListener {
            val intent = Intent(it.context, PlayerActivity::class.java).apply {
                putExtra("video_url", ep.playable_url)
                putExtra("title", "Capítulo ${ep.episode_number}: ${ep.name}")
                putExtra("is_live", false)
                ep.referer?.let { ref -> putExtra("referer", ref) }

                // AUTO-PLAY: Pasamos la lista de episodios y el número actual
                if (ep.episode_number != null && episodes.isNotEmpty()) {
                    putExtra("episode_number", ep.episode_number)
                    val episodeList = ArrayList<String>()
                    episodes.forEach { s ->
                        episodeList.add("${s.episode_number}|${s.playable_url}")
                    }
                    putStringArrayListExtra("episode_list", episodeList)
                }
            }
            it.context.startActivity(intent)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
    }
}
