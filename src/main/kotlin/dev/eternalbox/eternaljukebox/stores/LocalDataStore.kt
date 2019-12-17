package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalDataStore(val mountPoint: File, val analysisSupported: Boolean = true) : DataStore {
    companion object {
        private const val MINIMUM_SPACE_REQUIRED = 1_000_000
    }

    val analysisMount = File(mountPoint, "analysis")

    override suspend fun hasAnalysisStored(service: EnumAnalysisService, id: String): Boolean =
        File(analysisMount, "${service.name.toLowerCase()}${File.separator}$id.json").exists()

    override suspend fun canStoreAnalysis(service: EnumAnalysisService, id: String): Boolean =
        analysisMount.freeSpace > MINIMUM_SPACE_REQUIRED

    override suspend fun storeAnalysis(
        service: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> {
        val serviceMount = File(analysisMount, service.name.toLowerCase())
        if (!serviceMount.exists()) serviceMount.mkdirs()

        withContext(Dispatchers.IO) {
            FileOutputStream(File(serviceMount, "$id.json")).use { out ->
                out.write(data)
            }
        }

        return getAnalysis(service, id)
    }

    override suspend fun getAnalysis(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> =
        if (hasAnalysisStored(service, id)) {
            JukeboxResult.Success(DataResponse.ExternalUrl("/static/analysis/${service.name.toLowerCase()}/$id.json"))
        } else {
            JukeboxResult.KnownFailure(
                WebApiResponseCodes.ANALYSIS_NOT_STORED,
                WebApiResponseMessages.ANALYSIS_NOT_STORED
            )
        }
}