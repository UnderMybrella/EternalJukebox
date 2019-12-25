package dev.eternalbox.eternaljukebox.data.spotify

import com.fasterxml.jackson.annotation.JsonProperty
import dev.eternalbox.ytmusicapi.UnknownJsonObj

data class SpotifyTrack(
    val album: SpotifySimplifiedAlbum,
    val artists: SpotifySimplifiedArtist,
    @JsonProperty("available_markets") val availableMarkets: Array<String>,
    @JsonProperty("disc_number") val discNumber: Int,
    @JsonProperty("duration_ms") val durationMs: Int,
    val explicit: Boolean,
    @JsonProperty("external_ids") val externalIds: UnknownJsonObj,
    @JsonProperty("external_urls") val externalUrls: UnknownJsonObj,
    val href: String,
    val id: String,
    @JsonProperty("is_playable") val isPlayable: Boolean? = null,
    @JsonProperty("linked_from") val linkedFrom: SpotifyTrackLink? = null,
    val restrictions: UnknownJsonObj? = null,
    val name: String,
    val popularity: Int,
    @JsonProperty("preview_url") val previewUrl: String?,
    @JsonProperty("track_number") val trackNumber: Int,
    val type: String,
    val uri: String,
    @JsonProperty("is_local") val isLocal: Boolean
)