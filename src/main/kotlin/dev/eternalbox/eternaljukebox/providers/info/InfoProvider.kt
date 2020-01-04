package dev.eternalbox.eternaljukebox.providers.info

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

interface InfoProvider {
    suspend fun supportsTrackInfo(service: EnumAnalysisService): Boolean
    suspend fun retrieveTrackInfo(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse>
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
suspend fun InfoProvider.getTrackInfo(jukebox: EternalJukebox, service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
    if (jukebox.infoDataStore.hasTrackInfoStored(service, id))
        return jukebox.infoDataStore.getTrackInfo(service, id)
    else
        return retrieveTrackInfo(service, id)
}