package dev.eternalbox.audio.ytmsearch

import kotlin.time.Duration

data class YoutubeMusicSearchResult(
    val videoID: String,
    val songTitle: String,
    val songAlbum: String?,
    val songArtist: String?,
    val songDuration: Duration?,
    val thumbnails: List<YoutubeMusicThumbnail>
)
data class YoutubeMusicThumbnail(val url: String, val width: Int, val height: Int)