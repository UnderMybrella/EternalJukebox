package dev.eternalbox.eternaljukebox.data

data class EternalboxTrackInfo(
    val title: String,
    val artists: Array<String>,
    val album: String? = null
)