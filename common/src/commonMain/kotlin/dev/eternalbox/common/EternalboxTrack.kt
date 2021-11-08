package dev.eternalbox.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EternalboxTrack(
    val sections: List<EternalboxSectionAnalysis>,
    val bars: List<EternalboxBarAnalysis>,
    val beats: List<EternalboxBeatAnalysis>,
    val tatums: List<EternalboxTatumAnalysis>,
    val segments: List<EternalboxSegmentAnalysis>
)

@Serializable
data class EternalboxSectionAnalysis(
        val start: Double,
        val duration: Double,
        val confidence: Double,
        val loudness: Double,
        val tempo: Double,
        @SerialName("tempo_confidence") val tempoConfidence: Double,
        val key: Double,
        @SerialName("key_confidence") val keyConfidence: Double,
        val mode: Double,
        @SerialName("mode_confidence") val modeConfidence: Double,
        @SerialName("time_signature") val timeSignature: Double,
        @SerialName("time_signature_confidence") val timeSignatureConfidence: Double
)

@Serializable
data class EternalboxBarAnalysis(
        val start: Double,
        val duration: Double,
        val confidence: Double
)

@Serializable
data class EternalboxBeatAnalysis(
    val start: Double,
        val duration: Double,
        val confidence: Double
)

@Serializable
data class EternalboxTatumAnalysis(
        val start: Double,
        val duration: Double,
        val confidence: Double
)

@Serializable
data class EternalboxSegmentAnalysis(
        val start: Double,
        val duration: Double,
        val confidence: Double,
        @SerialName("loudness_start") val loudnessStart: Double,
        @SerialName("loudness_max") val loudnessMax: Double,
        @SerialName("loudness_max_time") val loudnessMaxTime: Double,

        val pitches: List<Double>,
        val timbre: List<Double>
)