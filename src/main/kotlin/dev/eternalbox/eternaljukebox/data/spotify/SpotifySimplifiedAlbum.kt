package dev.eternalbox.eternaljukebox.data.spotify

import com.fasterxml.jackson.annotation.JsonProperty
import dev.eternalbox.ytmusicapi.UnknownJsonObj

data class SpotifySimplifiedAlbum(
    @JsonProperty("album_group") val albumGroup: String? = null,
    @JsonProperty("album_type") val albumType: String,
    val artists: Array<SpotifySimplifiedArtist>,
    @JsonProperty("available_markets") val availableMarkets: Array<String>,
    @JsonProperty("external_urls") val externalUrls: UnknownJsonObj,
    val href: String,
    val id: String,
    val images: Array<UnknownJsonObj>,
    val name: String,
    @JsonProperty("release_date") val releaseDate: String,
    @JsonProperty("release_date_precision") val releaseDatePrecision: String,
    val restrictions: UnknownJsonObj? = null,
    val type: String,
    val uri: String
)