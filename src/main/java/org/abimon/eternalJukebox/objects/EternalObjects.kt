package org.abimon.eternalJukebox.objects

data class EternalAudio(
        val info: EternalInfo,
        val analysis: EternalAnalysis,
        val audio_summary: SpotifyAudioTrack
)

data class EternalAnalysis(
    val sections: Array<SpotifyAudioSection>,
    val bars: Array<SpotifyAudioBar>,
    val beats: Array<SpotifyAudioBeat>,
    val tatums: Array<SpotifyAudioTatum>,
    val segments: Array<SpotifyAudioSegment>
)

data class EternalInfo(
        val id: String,
        val name: String,
        val title: String,
        val artist: String,
        val url: String
)