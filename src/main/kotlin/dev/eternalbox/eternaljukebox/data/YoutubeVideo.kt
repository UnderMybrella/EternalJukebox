package dev.eternalbox.eternaljukebox.data

import java.time.ZonedDateTime

data class YoutubeVideo(
    val publishedAt: ZonedDateTime,
    val channelId: String,
    val title: String,
    val description: String,
    val thumbnails: Map<String, Thumbnail>,
    val channelTitle: String,
    val categoryId: String,
    val liveBroadcastContent: String,
    val defaultAudioLanguage: String?
) {
    data class Thumbnail(val url: String, val width: Int, val height: Int)
}