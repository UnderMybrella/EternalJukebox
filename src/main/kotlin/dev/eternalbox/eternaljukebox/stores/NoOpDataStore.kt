package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.data.*

object NoOpDataStore : AnalysisDataStore, AudioDataStore, InfoDataStore {
    override suspend fun hasAnalysisStored(service: EnumAnalysisService, id: String): Boolean = false
    override suspend fun canStoreAnalysis(service: EnumAnalysisService, id: String): Boolean = false

    override suspend fun storeAnalysis(
        service: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.ANALYSIS_NOT_SUPPORTED,
        WebApiResponseMessages.ANALYSIS_NOT_SUPPORTED
    )

    override suspend fun getAnalysis(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.ANALYSIS_NOT_SUPPORTED,
        WebApiResponseMessages.ANALYSIS_NOT_SUPPORTED
    )

    override suspend fun hasAudioStored(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = false

    override suspend fun canStoreAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): Boolean = false

    override suspend fun storeAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.AUDIO_NOT_SUPPORTED,
        WebApiResponseMessages.AUDIO_NOT_SUPPORTED
    )

    override suspend fun getAudio(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.AUDIO_NOT_SUPPORTED,
        WebApiResponseMessages.AUDIO_NOT_SUPPORTED
    )

    override suspend fun hasTrackInfoStored(service: EnumAnalysisService, id: String): Boolean = false
    override suspend fun canStoreTrackInfo(service: EnumAnalysisService, id: String): Boolean = false

    override suspend fun storeTrackInfo(
        service: EnumAnalysisService,
        id: String,
        data: ByteArray
    ): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.INFO_NOT_SUPPORTED,
        WebApiResponseMessages.INFO_NOT_SUPPORTED
    )

    override suspend fun getTrackInfo(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> = JukeboxResult.KnownFailure(
        WebApiResponseCodes.INFO_NOT_SUPPORTED,
        WebApiResponseMessages.INFO_NOT_SUPPORTED
    )
}