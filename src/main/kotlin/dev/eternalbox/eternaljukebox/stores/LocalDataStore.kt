package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.contracts.ExperimentalContracts

class LocalDataStore(
    analysisMountPath: String,
    audioMountPath: String,
    infoMountPath: String
) : AnalysisDataStore, AudioDataStore, InfoDataStore {
    companion object {
        private const val MINIMUM_SPACE_REQUIRED = 1_000_000_000 //Min 1 GB
    }

    @ExperimentalCoroutinesApi
    @ExperimentalContracts
    class Factory : DataStoreFactory<LocalDataStore> {
        override val name: String = "LocalDataStore"

        var mountPointPath: String = "data"
        var analysisMountPath: String? = null
        var audioMountPath: String? = null
        var infoMountPath: String? = null

        override fun configure(jukebox: EternalJukebox) {}
        override fun build(jukebox: EternalJukebox) = LocalDataStore(
            analysisMountPath ?: "$mountPointPath${File.separator}analysis",
            audioMountPath ?: "${mountPointPath}${File.separator}audio",
            infoMountPath ?: "${mountPointPath}${File.separator}info"
        )
    }

    val analysisMount = File(analysisMountPath)
    val audioMount = File(audioMountPath)
    val infoMount = File(infoMountPath)

    private fun analysisFile(service: EnumAnalysisService, id: String): File =
        File(analysisMount, "${service.name.toLowerCase()}${File.separator}$id.json")

    private fun audioFile(audioService: EnumAudioService, analysisService: EnumAnalysisService, id: String): File =
        File(
            audioMount,
            "${audioService.name.toLowerCase()}${File.separator}${analysisService.name.toLowerCase()}${File.separator}$id.m4a"
        )

    private fun infoFile(service: EnumAnalysisService, id: String): File =
        File(infoMount, "${service.name.toLowerCase()}${File.separator}$id.json")

    override suspend fun hasAnalysisStored(service: EnumAnalysisService, id: String): Boolean =
        analysisFile(service, id).exists()

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

    override suspend fun getAnalysis(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
        if (hasAnalysisStored(service, id)) {
            val analysisFile = analysisFile(service, id)
            return JukeboxResult.Success(
                DataResponse.ExternalUrl(
                    "/static/analysis/${service.name.toLowerCase()}/$id.json",
                    localData = DataResponse.DataSource(
                        analysisFile::inputStream,
                        "application/json",
                        analysisFile.length()
                    )
                )
            )
        } else {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.NOT_FOUND,
                WebApiResponseMessages.ANALYSIS_NOT_STORED
            )
        }
    }

    override suspend fun hasAudioStored(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = audioFile(audioService, analysisService, id).exists()

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
        val serviceMount =
            File(audioMount, "${audioService.name.toLowerCase()}${File.separator}${analysisService.name.toLowerCase()}")
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
    ): JukeboxResult<DataResponse> {
        if (hasAudioStored(audioService, analysisService, id)) {
            val audioFile = audioFile(audioService, analysisService, id)
            return JukeboxResult.Success(
                DataResponse.ExternalUrl(
                    "/static/audio/${audioService.name.toLowerCase()}/${analysisService.name.toLowerCase()}/$id.m4a",
                    localData = DataResponse.DataSource(audioFile::inputStream, "audio/m4a", audioFile.length())
                )
            )
        } else {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.NOT_FOUND,
                WebApiResponseMessages.AUDIO_NOT_STORED
            )
        }
    }

    override suspend fun hasTrackInfoStored(service: EnumAnalysisService, id: String): Boolean =
        infoFile(service, id).exists()

    override suspend fun canStoreTrackInfo(service: EnumAnalysisService, id: String): Boolean =
        infoMount.freeSpace > MINIMUM_SPACE_REQUIRED

    override suspend fun storeTrackInfo(
        service: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> {
        val serviceMount = File(infoMount, service.name.toLowerCase())
        if (!serviceMount.exists()) serviceMount.mkdirs()

        withContext(Dispatchers.IO) {
            FileOutputStream(File(serviceMount, "$id.json")).use { out ->
                out.write(data)
            }
        }

        return getTrackInfo(service, id)
    }

    override suspend fun getTrackInfo(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
        if (hasTrackInfoStored(service, id)) {
            val infoFile = infoFile(service, id)
            return JukeboxResult.Success(
                DataResponse.ExternalUrl(
                    "/static/info/${service.name.toLowerCase()}/$id.json",
                    localData = DataResponse.DataSource(infoFile::inputStream, "application/json", infoFile.length())
                )
            )
        } else {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.NOT_FOUND,
                WebApiResponseMessages.INFO_NOT_STORED
            )
        }
    }
}