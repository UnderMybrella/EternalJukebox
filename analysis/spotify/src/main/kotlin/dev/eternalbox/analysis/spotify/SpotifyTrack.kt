package dev.eternalbox.analysis.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTrack(
    val album: SpotifyAlbum,
    val artists: List<SpotifyArtist>,
    @SerialName("available_markets")
    val availableMarkets: List<String>,
    @SerialName("disc_number")
    val discNumber: Int,
    @SerialName("duration_ms")
    val durationMs: Int,
    val explicit: Boolean,
    @SerialName("external_ids")
    val externalIDs: SpotifyExternalIDs,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    @SerialName("is_playable")
    val isPlayable: Boolean = true,
    @SerialName("linked_from")
    val linkedFrom: SpotifyTrack? = null,
    val restrictions: SpotifyRestrictions? = null,
    val name: String,
    val popularity: Int,
    @SerialName("preview_url")
    val previewUrl: String?,
    @SerialName("track_number")
    val trackNumber: Int,
    val type: String,
    val uri: String,
    @SerialName("is_local")
    val isLocal: Boolean
)

@Serializable
data class SpotifyAlbum(
    @SerialName("album_type")
    val albumType: String,
    @SerialName("total_tracks")
    val totalTracks: Int,
    @SerialName("available_markets")
    val availableMarkets: List<String>,
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val images: List<SpotifyImage>,
    val name: String,
    @SerialName("release_date")
    val releaseDate: String,
    @SerialName("release_date_precision")
    val releaseDatePrecision: String,
    val restrictions: SpotifyRestrictions? = null,
    val type: String,
    val uri: String,
    @SerialName("album_group")
    val albumGroup: String? = null,
    val artists: List<SpotifyAlbumArtist>
)

@Serializable
data class SpotifyAlbumArtist(
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val href: String,
    val id: String,
    val name: String,
    val type: String,
    val uri: String
)

@Serializable
data class SpotifyExternalUrls(
    val spotify: String
)

@Serializable
data class SpotifyArtist(
    @SerialName("external_urls")
    val externalUrls: SpotifyExternalUrls,
    val followers: SpotifyArtistFollowers? = null,
    val genres: List<String> = emptyList(),
    val href: String,
    val id: String,
    val images: List<SpotifyImage> = emptyList(),
    val name: String,
    val popularity: Int = 0,
    val type: String,
    val uri: String
)

@Serializable
data class SpotifyArtistFollowers(
    val href: String?,
    val total: Int
)

@Serializable
data class SpotifyImage(
    val url: String,
    val height: Int,
    val width: Int
)

@Serializable
data class SpotifyExternalIDs(
    val isrc: String? = null,
    val ean: String? = null,
    val upc: String? = null
)

@Serializable
data class SpotifyRestrictions(
    val reason: String
)