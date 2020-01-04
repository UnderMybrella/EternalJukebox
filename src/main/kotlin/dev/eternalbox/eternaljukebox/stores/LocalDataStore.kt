package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalDataStore(
    val mountPoint: File,
    val analysisSupported: Boolean = true,
    val audioSupported: Boolean = true,
    val infoSupported: Boolean = true
) : DataStore {
    companion object {
        private const val MINIMUM_SPACE_REQUIRED = 1_000_000_000 //Min 1 GB
    }

    val analysisMount = File(mountPoint, "analysis")
    val audioMount = File(mountPoint, "audio")
    val infoMount = File(mountPoint, "info")

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
        analysisSupported && analysisFile(service, id).exists()

    override suspend fun canStoreAnalysis(service: EnumAnalysisService, id: String): Boolean =
        analysisSupported && analysisMount.freeSpace > MINIMUM_SPACE_REQUIRED

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
        if (!analysisSupported) {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.ANALYSIS_NOT_SUPPORTED,
                WebApiResponseMessages.ANALYSIS_NOT_SUPPORTED
            )
        } else if (hasAnalysisStored(service, id)) {
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
    ): Boolean = audioSupported && audioFile(audioService, analysisService, id).exists()

    override suspend fun canStoreAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = audioSupported && audioMount.freeSpace > MINIMUM_SPACE_REQUIRED

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
        if (!audioSupported) {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.AUDIO_NOT_SUPPORTED,
                WebApiResponseMessages.AUDIO_NOT_SUPPORTED
            )
        } else if (hasAudioStored(audioService, analysisService, id)) {
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
        infoSupported && infoFile(service, id).exists()

    override suspend fun canStoreTrackInfo(service: EnumAnalysisService, id: String): Boolean =
        infoSupported && infoMount.freeSpace > MINIMUM_SPACE_REQUIRED

    override suspend fun storeTrackInfo(
        service: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> {
        if (!infoSupported) {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.INFO_NOT_SUPPORTED,
                WebApiResponseMessages.INFO_NOT_SUPPORTED
            )
        }

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
        if (!infoSupported) {
            return JukeboxResult.KnownFailure(
                WebApiResponseCodes.INFO_NOT_SUPPORTED,
                WebApiResponseMessages.INFO_NOT_SUPPORTED
            )
        } else if (hasTrackInfoStored(service, id)) {
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