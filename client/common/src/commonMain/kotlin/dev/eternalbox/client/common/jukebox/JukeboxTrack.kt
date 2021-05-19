package dev.eternalbox.client.common.jukebox

import dev.eternalbox.client.common.*
import kotlinx.serialization.SerialName
import kotlin.properties.Delegates

class JukeboxTrack(val sections: Array<JukeboxSectionAnalysis>, val bars: Array<JukeboxBarAnalysis>, val beats: Array<JukeboxBeatAnalysis>, val tatums: Array<JukeboxTatumAnalysis>, val segments: Array<JukeboxSegmentAnalysis>) {
    val filteredSegments: MutableList<JukeboxSegmentAnalysis> = ArrayList()
}

data class JukeboxEdge<T : JukeboxSegmentedType<T>>(val id: Int, val src: T, val dest: T, val distance: Double) : Comparable<JukeboxEdge<T>> {
    override fun compareTo(other: JukeboxEdge<T>): Int = distance.compareTo(other.distance)
}

abstract class JukeboxAnalysisType<SELF : JukeboxAnalysisType<SELF>> {
    abstract val start: Double
    abstract val duration: Double
    abstract val confidence: Double
    val end: Double
        get() = start + duration

    lateinit var track: JukeboxTrack
    var which: Int = -1
    var reach: Int = -1
    var prev: SELF? = null
    var next: SELF? = null

    val children: MutableList<JukeboxAnalysisType<*>> = ArrayList()
    var parent: JukeboxAnalysisType<*>? = null
    var indexInParent: Int? = null
}

abstract class JukeboxSegmentedType<SELF : JukeboxSegmentedType<SELF>> : JukeboxAnalysisType<SELF>() {
    var oseg: JukeboxSegmentAnalysis? = null
    val overlappingSegments: MutableList<JukeboxSegmentAnalysis> = ArrayList()

    val neighbours: MutableList<JukeboxEdge<SELF>> = ArrayList()
    val allNeighbours: MutableList<JukeboxEdge<SELF>> = ArrayList()
}

data class JukeboxSectionAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double,
        val loudness: Double,
        val tempo: Double,
        @SerialName("tempo_confidence") val tempoConfidence: Double,
        val key: Double,
        @SerialName("key_confidence") val keyConfidence: Double,
        val mode: Double,
        @SerialName("mode_confidecne") val modeConfidence: Double,
        @SerialName("time_signature") val timeSignature: Double,
        @SerialName("time_signature_confidence") val timeSignatureConfidence: Double
) : JukeboxAnalysisType<JukeboxSectionAnalysis>()

data class JukeboxBarAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : JukeboxSegmentedType<JukeboxBarAnalysis>()

data class JukeboxBeatAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : JukeboxSegmentedType<JukeboxBeatAnalysis>()

data class JukeboxTatumAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
) : JukeboxSegmentedType<JukeboxTatumAnalysis>()

data class JukeboxSegmentAnalysis(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double,
        @SerialName("loudness_start") val loudnessStart: Double,
        @SerialName("loudness_max") val loudnessMax: Double,
        @SerialName("loudness_max_time") val loudnessMaxTime: Double,

        val pitches: DoubleArray,
        val timbre: DoubleArray
) : JukeboxAnalysisType<JukeboxSegmentAnalysis>()

inline fun EternalboxTrack.toJukebox(): JukeboxTrack = JukeboxTrack(
        Array(sections.size) { i -> sections[i].toJukebox() },
        Array(bars.size) { i -> bars[i].toJukebox() },
        Array(beats.size) { i -> beats[i].toJukebox() },
        Array(tatums.size) { i -> tatums[i].toJukebox() },
        Array(segments.size) { i -> segments[i].toJukebox() }
)

inline fun EternalboxSectionAnalysis.toJukebox(): JukeboxSectionAnalysis =
        JukeboxSectionAnalysis(start, duration, confidence, loudness, tempo, tempoConfidence, key, keyConfidence, mode, modeConfidence, timeSignature, timeSignatureConfidence)

inline fun EternalboxBarAnalysis.toJukebox(): JukeboxBarAnalysis =
        JukeboxBarAnalysis(start, duration, confidence)

inline fun EternalboxBeatAnalysis.toJukebox(): JukeboxBeatAnalysis =
        JukeboxBeatAnalysis(start, duration, confidence)

inline fun EternalboxTatumAnalysis.toJukebox(): JukeboxTatumAnalysis =
        JukeboxTatumAnalysis(start, duration, confidence)

inline fun EternalboxSegmentAnalysis.toJukebox(): JukeboxSegmentAnalysis =
        JukeboxSegmentAnalysis(start, duration, confidence, loudnessStart, loudnessMax, loudnessMaxTime, pitches, timbre)