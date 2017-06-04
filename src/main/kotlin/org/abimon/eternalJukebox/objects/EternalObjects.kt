package org.abimon.eternalJukebox.objects

data class EternalInfo(
        val info: EternalMetadata,
        val analysis: EternalAnalysis,
        val audio_summary: SpotifyAudioTrack
)

data class EternalAnalysis(
    val sections: Array<SpotifyAudioSection>,
    val bars: Array<SpotifyAudioBar>,
    val beats: Array<SpotifyAudioBeat>,
    val tatums: Array<SpotifyAudioTatum>,
    val segments: Array<SpotifyAudioSegment>,
    var fsegments: ArrayList<SpotifyAudioSegment>? = null
)

data class EternalMetadata(
        var id: String,
        val name: String,
        val title: String,
        val artist: String,
        val url: String
)

open class AnalysisType(open val start: Double, open val duration: Double, open val confidence: Double) {
    var track: EternalInfo? = null
    var which: Int? = null
    var prev: AnalysisType? = null
    var next: AnalysisType? = null
    val children = ArrayList<AnalysisType>()
    var parent: AnalysisType? = null
    var indexInParent: Int? = null
    var oseg: SpotifyAudioSegment? = null
    val overlappingSegments = ArrayList<SpotifyAudioSegment>()
}

