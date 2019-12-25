package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalDataStore(val mountPoint: File, val analysisSupported: Boolean = true) : DataStore {
    companion object {
        private const val MINIMUM_SPACE_REQUIRED = 1_000_000_000 //Min 1 GB
    }

    val analysisMount = File(mountPoint, "analysis")
    val audioMount = File(mountPoint, "audio")

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
                WebApiResponseCodes.NOT_FOUND,
                WebApiResponseMessages.ANALYSIS_NOT_STORED
            )
        }

    override suspend fun hasAudioStored(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = File(
        audioMount,
        "${audioService.name.toLowerCase()}${File.separator}${analysisService.name.toLowerCase()}${File.separator}$id.m4a"
    ).exists()

    override suspend fun canStoreAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = audioMount.freeSpace > MINIMUM_SPACE_REQUIRED

    override suspend fun storeAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> {
        val serviceMount = File(audioMount, "${audioService.name.toLowerCase()}${File.separator}${analysisService.name.toLowerCase()}")
        if (!serviceMount.exists()) serviceMount.mkdirs()

        withContext(Dispatchers.IO) {
            FileOutputStream(File(serviceMount, "$id.m4a")).use { out ->
                out.write(data)
            }
        }

        return getAudio(audioService, analysisService, id)
    }

    override suspend fun getAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): JukeboxResult<DataResponse> =
        if (hasAudioStored(audioService, analysisService, id)) {
            JukeboxResult.Success(DataResponse.ExternalUrl("/static/audio/${audioService.name.toLowerCase()}/${analysisService.name.toLowerCase()}/$id.m4a"))
        } else {
            JukeboxResult.KnownFailure(
                WebApiResponseCodes.NOT_FOUND,
                WebApiResponseMessages.AUDIO_NOT_STORED
            )
        }
}