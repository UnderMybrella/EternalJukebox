package dev.eternalbox.eternaljukebox.data.spotify

import com.fasterxml.jackson.annotation.JsonProperty

data class SpotifyTrackAnalysis(
    val meta: Meta,
    val track: Track,
    val bars: Array<Bar>,
    val beats: Array<Beat>,
    val tatums: Array<Tatum>,
    val sections: Array<Section>,
    val segments: Array<Segment>
) {
    data class Meta(
        @JsonProperty("analyzer_version") val analyserVersion: String,
        val platform: String,
        @JsonProperty("detailed_status") val detailedStatus: String,
        @JsonProperty("status_code") val statusCode: Int,
        val timestamp: Long,
        @JsonProperty("analysis_time") val analysisTime: Double,
        @JsonProperty("input_process") val inputProcess: String
    )

    data class Track(
        @JsonProperty("num_samples") val numSamples: Int,
        val duration: Double,
        @JsonProperty("sample_md5") val sampleMD5: String,
        @JsonProperty("offset_seconds") val offsetSeconds: Int,
        @JsonProperty("window_seconds") val windowSeconds: Int,
        @JsonProperty("analysis_sample_rate") val analysisSampleRate: Int,
        @JsonProperty("analysis_channels") val analysisChannels: Int,
        @JsonProperty("end_of_fade_in") val endOfFadeIn: Double,
        @JsonProperty("start_of_fade_out") val startOfFadeOut: Double,
        val loudness: Double,
        val tempo: Double,
        @JsonProperty("tempo_confidence") val tempoConfidence: Double,
        @JsonProperty("time_signature") val timeSignature: Int,
        @JsonProperty("time_signature_confidence") val timeSignatureConfidence: Double,
        val key: Int,
        @JsonProperty("key_confidence") val keyConfidence: Double,
        val mode: Int,
        @JsonProperty("mode_confidence") val modeConfidence: Double,
        @JsonProperty("codestring") val codeString: String,
        @JsonProperty("code_version") val codeVersion: Double,
        @JsonProperty("echoprintstring") val echoprintString: String,
        @JsonProperty("echoprint_version") val echoprintVersion: Double,
        @JsonProperty("synchstring") val synchString: String,
        @JsonProperty("synch_version") val synchVersion: Double,
        @JsonProperty("rhythmstring") val rhythmString: String,
        @JsonProperty("rhythm_version") val rhythmVersion: Double
    )

    data class Bar(val start: Double, val duration: Double, val confidence: Double)
    data class Beat(val start: Double, val duration: Double, val confidence: Double)
    data class Tatum(val start: Double, val duration: Double, val confidence: Double)
    data class Section(
        val start: Double,
        val duration: Double,
        val confidence: Double,
        val loudness: Double,
        val tempo: Double,
        @JsonProperty("tempo_confidence") val tempoConfidence: Double,
        val key: Int,
        @JsonProperty("key_confidence") val keyConfidence: Double,
        val mode: Int,
        @JsonProperty("mode_confidence") val modeConfidence: Double,
        @JsonProperty("time_signature") val timeSignature: Int,
        @JsonProperty("time_signature_confidence") val timeSignatureConfidence: Double
    )

    data class Segment(
        val start: Double,
        val duration: Double,
        val confidence: Double,
        @JsonProperty("loudness_start") val loudnessStart: Double,
        @JsonProperty("loudness_max_time") val loudnessMaxTime: Double,
        @JsonProperty("loudness_max") val loudnessMax: Double,
        val pitches: DoubleArray,
        val timbre: DoubleArray
    )
}