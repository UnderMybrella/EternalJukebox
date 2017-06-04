package org.abimon.eternalJukebox.objects

data class SpotifyTrack(
        val href: String,
        val id: String,
        val name: String,
        val uri: String,
        val duration_ms: Long,
        val explicit: Boolean,
        val artists: Array<SpotifyArtist>
)

data class SpotifyArtist(
    val id: String,
    val name: String
)

data class SpotifyAudio(
        val meta: SpotifyAudioMeta,
        val track: SpotifyAudioTrack,
        val bars: Array<SpotifyAudioBar>,
        val beats: Array<SpotifyAudioBeat>,
        val tatums: Array<SpotifyAudioTatum>,
        val sections: Array<SpotifyAudioSection>,
        val segments: Array<SpotifyAudioSegment>
)

data class SpotifyAudioMeta(
        val analyzer_version: String,
        val platform: String,
        val detailed_status: String,
        val status_code: Int,
        val analysis_time: Double,
        val input_process: String
)

data class SpotifyAudioTrack(
        val duration: Double,
        val loudness: Double,
        val tempo: Int,
        val time_signature: Int,
        val key: Int,
        val mode: Int
)

data class SpotifyAudioBar(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
): AnalysisType(start, duration, confidence)

data class SpotifyAudioBeat(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
): AnalysisType(start, duration, confidence)

data class SpotifyAudioTatum(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double
): AnalysisType(start, duration, confidence)

data class SpotifyAudioSection(
        override val start: Double,
        override val duration: Double,
        override val confidence: Double,
        val loudness: Double,
        val tempo: Double,
        val tempo_confidence: Double,
        val key: Int,
        val key_confidence: Double,
        val mode: Int,
        val mode_confidence: Double,
        val time_signature: Int,
        val time_signature_confidence: Double
): AnalysisType(start, duration, confidence)

data class SpotifyAudioSegment(
        override val start: Double,
        override var duration: Double,
        override val confidence: Double,
        val loudness_start: Int,
        val loudness_max_time: Int,
        val loudness_max: Int,
        val pitches: DoubleArray,
        val timbre: DoubleArray
): AnalysisType(start, duration, confidence)