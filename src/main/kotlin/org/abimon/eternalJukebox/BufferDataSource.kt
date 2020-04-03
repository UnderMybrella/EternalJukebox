package org.abimon.eternalJukebox

import io.vertx.core.buffer.Buffer
import org.abimon.visi.io.DataSource
import java.io.InputStream
import kotlin.math.min

data class BufferDataSource(val buffer: Buffer): DataSource {
    override val data: ByteArray
        get() = buffer.bytes
    override val inputStream: InputStream
        get() = BufferInputStream(buffer)
    override val location: String
        get() = "Buffer $buffer"
    override val seekableInputStream: InputStream
        get() = inputStream
    override val size: Long
        get() = buffer.length().toLong()

}

class BufferInputStream(
    private val buffer: Buffer,
    private var pos: Int = 0,
    private var size: Int = buffer.length(),
    private var mark: Int = 0
) : InputStream() {

    override fun read(): Int = if (pos < size) buffer.getByte(pos++).toInt() and 0xFF else -1
    override fun read(b: ByteArray): Int = read(b, 0, b.size)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len < 0 || off < 0 || len > b.size - off)
            throw IndexOutOfBoundsException()

        if (pos >= size)
            return -1

        val avail = size - pos

        @Suppress("NAME_SHADOWING")
        val len: Int = if (len > avail) avail else len
        if (len <= 0)
            return 0

        buffer.getBytes(pos, pos + len, b, off)
        pos += len
        return len
    }

    override fun skip(n: Long): Long {
        val k = min((size - pos).toLong(), n)
        pos += k.toInt()
        return k
    }

    override fun available(): Int = size - pos

    override fun reset() {
        pos = mark
    }

    override fun mark(readlimit: Int) {
        mark = pos
    }

    override fun markSupported(): Boolean {
        return true
    }
}