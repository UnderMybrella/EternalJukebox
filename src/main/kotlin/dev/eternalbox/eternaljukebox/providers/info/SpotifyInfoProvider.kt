package dev.eternalbox.eternaljukebox.providers.info

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.EternalboxTrackInfo
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
@ExperimentalCoroutinesApi
class SpotifyInfoProvider(val jukebox: EternalJukebox) : InfoProvider {
    class Factory: InfoProviderFactory<SpotifyInfoProvider> {
        override val name: String = "SpotifyInfoProvider"

        override fun configure(jukebox: EternalJukebox) {}
        override fun build(jukebox: EternalJukebox): SpotifyInfoProvider = SpotifyInfoProvider(jukebox)
    }

    override suspend fun supportsTrackInfo(service: EnumAnalysisService): Boolean =
        service == EnumAnalysisService.SPOTIFY

    override suspend fun retrieveTrackInfoFor(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
        when (service) {
            EnumAnalysisService.SPOTIFY -> return jukebox.spotifyApi.getTrack(id)
                .map { track ->
                    EternalboxTrackInfo(
                        track.name,
                        Array(track.artists.size) { track.artists[it].name },
                        track.album.name
                    )
                }
                .flatMapAwait { track ->
                    jukebox.infoDataStore.storeTrackInfo(
                        service,
                        id,
                        withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsBytes(track) }
                    )
                }
            EnumAnalysisService.JSON -> TODO()
        }
    }
}