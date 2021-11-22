package dev.eternalbox.audio

enum class EnumAudioType (val youtubeDLName: String, val extension: String, val mimeType: String) {
    OPUS("opus", "opus", "audio/ogg"),
    M4A("m4a", "m4a", "audio/mp4"),
    MP3("mp3", "mp3", "audio/mp3"),
    OGG("vorbis", "ogg", "audio/ogg")
}