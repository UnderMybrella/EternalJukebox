package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.JukeboxResult

interface DataStore {
    suspend fun hasAnalysisStored(service: EnumAnalysisService, id: String): Boolean
    suspend fun canStoreAnalysis(service: EnumAnalysisService, id: String): Boolean
    suspend fun storeAnalysis(service: EnumAnalysisService, id: String, data: ByteArray): JukeboxResult<DataResponse>
    suspend fun getAnalysis(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse>
}