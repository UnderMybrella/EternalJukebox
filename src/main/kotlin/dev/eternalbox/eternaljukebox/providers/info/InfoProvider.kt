package dev.eternalbox.eternaljukebox.providers.info

import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.JukeboxResult

interface InfoProvider {
    suspend fun supportsTrackInfo(service: EnumAnalysisService): Boolean
    suspend fun retrieveTrackInfoFor(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse>
}