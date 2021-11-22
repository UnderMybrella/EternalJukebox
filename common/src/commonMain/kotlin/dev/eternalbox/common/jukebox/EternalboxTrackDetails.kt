package dev.eternalbox.common.jukebox

import kotlinx.serialization.Serializable

@Serializable
data class EternalboxTrackDetails(
    val service: String,
    val id: String,
    val name: String,
    val albumName: String?,
    val imageUrl: String?,
    val artists: List<String>,
    val durationMs: Int,
    val isrc: String?
)