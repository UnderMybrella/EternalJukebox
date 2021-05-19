package dev.eternalbox.client.common

import dev.brella.kornea.errors.common.KorneaResult
import dev.brella.kornea.io.common.BaseDataCloseable
import dev.brella.kornea.io.common.Uri
import dev.brella.kornea.io.common.flow.CountingOutputFlow

@ExperimentalUnsignedTypes
class BufferWrapOutputFlow(val buffer: ByteArray) : BaseDataCloseable(), CountingOutputFlow {
    var pos: Int = 0

    override val streamOffset: Long
        get() = buffer.size.toLong()

    override suspend fun write(byte: Int) {
        buffer[pos++] = byte.toByte()
    }

    override suspend fun write(b: ByteArray) = write(b, 0, b.size)
    override suspend fun write(b: ByteArray, off: Int, len: Int) {
//        System.arraycopy(b, off, buffer, pos, len)
        b.copyInto(buffer, pos, off, off + len)
        pos += len
    }

    override suspend fun flush() {}

    fun getData(): ByteArray = buffer.copyOf()
    fun getDataSize(): ULong = buffer.size.toULong()

    override fun locationAsUri(): KorneaResult<Uri> = KorneaResult.empty()
}