package dev.eternalbox.eternaljukebox.data

import dev.eternalbox.eternaljukebox.data.spotify.SpotifySimplifiedArtist
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrack
import dev.eternalbox.eternaljukebox.mapArray

data class EternalBoxSearchResult(
    val analysisService: EnumAnalysisService,
    val analysisID: String,
    val title: String,
    val artists: Array<String>,
    val album: String,
    val thumbnail: String?
) {
    constructor(track: SpotifyTrack) : this(
        EnumAnalysisService.SPOTIFY,
        track.id,
        track.name,
        track.artists.mapArray(SpotifySimplifiedArtist::name),
        track.album.name,
        track.album.images.maxBy { image -> if (image.width == null || image.height == null) 0 else image.width * image.height }?.url
    )
}