package dev.eternalbox.analysis

import dev.eternalbox.common.EternalboxTrack
import dev.eternalbox.common.jukebox.EternalboxTrackDetails

interface AnalysisApi {
    val service: String

    suspend fun getAnalysis(trackID: String): EternalboxTrack?
    suspend fun getTrackDetails(trackID: String): EternalboxTrackDetails?
}