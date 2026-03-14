package app.cuevanatv.providers

import app.cuevanatv.model.VideoItem

object StaticProvider : ContentProvider {
    override fun getFeatured(): List<VideoItem> {
        return listOf(
            VideoItem(
                title = "Big Buck Bunny",
                imageUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            ),
            VideoItem(
                title = "Tears of Steel",
                imageUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            ),
            VideoItem(
                title = "Sintel",
                imageUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
            )
        )
    }
}

