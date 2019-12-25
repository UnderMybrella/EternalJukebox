package dev.eternalbox.eternaljukebox.providers.audio

import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.EnumAudioService

interface AudioProvider {
    suspend fun supportsAudio(audioService: EnumAudioService, analysisService: EnumAnalysisService): Boolean
//    suspend fun getAudioFor(service: EnumAudioService, analysisService: EnumAnalysisService, id: String): DataResponse?
}
