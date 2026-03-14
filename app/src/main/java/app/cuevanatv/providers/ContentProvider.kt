package app.cuevanatv.providers

import app.cuevanatv.model.VideoItem

interface ContentProvider {
    fun getFeatured(): List<VideoItem>
}

