package dev.eternalbox.eternaljukebox.providers.analysis

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.providers.analysis.AnalysisProvider.Companion.parseAnalysisData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class SpotifyAnalysisProvider(val jukebox: EternalJukebox) : AnalysisProvider {
    override suspend fun supportsAnalysis(service: EnumAnalysisService): Boolean =
        service == EnumAnalysisService.SPOTIFY

    override suspend fun retrieveAnalysisFor(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
        if (service != EnumAnalysisService.SPOTIFY) return JukeboxResult.KnownFailure(
            WebApiResponseCodes.INVALID_ANALYSIS_SERVICE,
            WebApiResponseMessages.INVALID_ANALYSIS_SERVICE,
            service
        )

        return jukebox.spotifyApi.getTrackAnalysis(id)
            .flatMap { track -> parseAnalysisData(track, id) }
            .flatMapAwait { json -> jukebox.analysisDataStore.storeAnalysis(service, id, withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsBytes(json) }) }
    }
}