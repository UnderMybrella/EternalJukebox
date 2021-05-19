package dev.eternalbox.storage

import org.springframework.util.MimeType

sealed class EternalData<T> {
    data class TextData(override val tag: String, override val data: String): EternalData<String>()
    data class BinaryData(override val tag: String, override val data: ByteArray, val mimeType: MimeType): EternalData<ByteArray>()

    abstract val tag: String
    abstract val data: T
}