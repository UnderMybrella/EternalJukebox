package dev.eternalbox.audio

import dev.eternalbox.common.jukebox.EternalboxTrackDetails
import dev.eternalbox.storage.base.EternalData

interface AudioApi {
    val service: String

    suspend fun getAudio(url: String, type: EnumAudioType, track: EternalboxTrackDetails): EternalData?
}