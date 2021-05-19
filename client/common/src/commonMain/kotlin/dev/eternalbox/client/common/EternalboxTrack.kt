package dev.eternalbox.client.common

import dev.eternalbox.client.common.jukebox.JukeboxEdge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.properties.Delegates

@Serializable
class EternalboxTrack(val sections: Array<EternalboxSectionAnalysis>, val bars: Array<EternalboxBarAnalysis>, val beats: Array<EternalboxBeatAnalysis>, val tatums: Array<EternalboxTatumAnalysis>, val segments: Array<EternalboxSegmentAnalysis>) {
    val filteredSegments: MutableList<EternalboxSegmentAnalysis> = ArrayList()
}

@Serializable
abstract class EternalboxAnalysisType<SELF: EternalboxAnalysisType<SELF>> {
    abstract val start: Double
    abstract val duration: Double
    abstract val confidence: Double
    val end: Double
        get() = start + duration

    @Transient
    lateinit var track: EternalboxTrack
    @Transient
    var which: Int = -1
    @Transient
    var prev: SELF? = null
    @Transient
    var next: SELF? = null

    @Transient
    val children: MutableList<EternalboxAnalysisType<*>> = ArrayList()
    @Transient
    var parent: EternalboxAnalysisType<*>? = null
    @Transient
    var indexInParent: Int? = null
}

@Serializable
abstract class EternalboxSegmentedType<SELF: EternalboxSegmentedType<SELF>>: EternalboxAnalysisType<SELF>() {
    @Transient
    var oseg: EternalboxSegmentAnalysis? = null
    @Transient
    val overlappingSegments: MutableList<EternalboxSegmentAnalysis> = ArrayList()
}

@Serializable
data class EternalboxSectionAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double,
        val loudness: Double,
        val tempo: Double,
        @SerialName("tempo_confidence") val tempoConfidence: Double,
        val key: Double,
        @SerialName("key_confidence") val keyConfidence: Double,
        val mode: Double,
        @SerialName("mode_confidence") val modeConfidence: Double,
        @SerialName("time_signature") val timeSignature: Double,
        @SerialName("time_signature_confidence") val timeSignatureConfidence: Double
) : EternalboxAnalysisType<EternalboxSectionAnalysis>()

@Serializable
data class EternalboxBarAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : EternalboxSegmentedType<EternalboxBarAnalysis>()

@Serializable
data class EternalboxBeatAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : EternalboxSegmentedType<EternalboxBeatAnalysis>()

@Serializable
data class EternalboxTatumAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : EternalboxSegmentedType<EternalboxTatumAnalysis>()

@Serializable
data class EternalboxSegmentAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double,
        @SerialName("loudness_start") val loudnessStart: Double,
        @SerialName("loudness_max") val loudnessMax: Double,
        @SerialName("loudness_max_time") val loudnessMaxTime: Double,

        val pitches: DoubleArray,
        val timbre: DoubleArray
) : EternalboxAnalysisType<EternalboxSegmentAnalysis>()