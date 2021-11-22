package dev.eternalbox.storage.base

sealed class EternalData {
    data class Raw(val data: ByteArray, val name: String?, val contentType: String): EternalData()
    data class Uploaded(val url: String): EternalData()
}
