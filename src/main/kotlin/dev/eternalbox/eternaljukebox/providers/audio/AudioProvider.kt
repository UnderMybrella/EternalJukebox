package dev.eternalbox.eternaljukebox.providers.audio

import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.EnumAudioService
import dev.eternalbox.eternaljukebox.data.JukeboxResult

interface AudioProvider {
    suspend fun supportsAudio(audioService: EnumAudioService, analysisService: EnumAnalysisService): Boolean
    suspend fun retrieveAudioFor(audioService: EnumAudioService, analysisService: EnumAnalysisService, id: String): JukeboxResult<DataResponse>
//    suspend fun getAudioFor(service: EnumAudioService, analysisService: EnumAnalysisService, id: String): DataResponse?
}
