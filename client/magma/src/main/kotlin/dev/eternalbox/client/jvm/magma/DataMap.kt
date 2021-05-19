package dev.eternalbox.client.jvm.magma

import java.nio.ByteBuffer
import kotlin.collections.HashMap

sealed class DataMap<K> {
    class MemoryEfficient<K> : DataMap<K>() {
        override fun grow(minSize: Int) {
            buffer = buffer.copyOf(minSize)
        }
    }

    class Default<K> : DataMap<K>() {
        override fun grow(minSize: Int) {
            val oldCapacity = buffer.size
            val newCapacity = oldCapacity + (oldCapacity shr 1)
            buffer = buffer.copyOf(maxOf(newCapacity, minSize))
        }
    }

    class MemoryGenerous<K> : DataMap<K>() {
        override fun grow(minSize: Int) {
            buffer = buffer.copyOf(maxOf(buffer.size shl 1, minSize))
        }
    }

    protected val indices: MutableMap<K, Long> = HashMap()
    protected var buffer: ByteArray = ByteArray(0)
    protected var writeIndex: Int = 0

    protected abstract fun grow(minSize: Int)

    suspend fun getSubBufferPositions(key: K, positions: IntArray, sizes: IntArray): Int {
        val tmp = indices[key] ?: return 0
        val startPos = (tmp and 0xFFFFFFFF).toInt()
        val size = ((tmp shr 32) and 0xFFFFFFFF).toInt()
        val end = startPos + size
        var pos = startPos
        var count = 0

        while (true) {
            val len = (buffer[pos++].toInt().and(0xFF) or (buffer[pos++].toInt().and(0xFF).shl(8))) and 0xFFFF //Don't go backwards
            if (pos + len >= end) return count
            positions[count] = pos
            sizes[count] = len
            count++

            pos += len
        }
    }

    fun getFromBuffer(pos: Int, len: Int, bytebuf: ByteBuffer) {
        bytebuf.put(buffer, pos, len)
    }

    fun getFromBuffer(key: K): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        val data = ByteArray(size)
        getFromBuffer(start, data, 0, size)
        return data
    }

    /** Note: This method assumes b can hold the size */
    fun getFromBuffer(key: K, b: ByteArray): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start, b, 0, size)
        return b
    }

    fun getFromBufferSafeLength(key: K, b: ByteArray): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start, b, 0, minOf(b.size, size))
        return b
    }

    /** Note: This method assumes b can hold the size */
    fun getFromBuffer(key: K, b: ByteArray, off: Int, len: Int): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        getFromBuffer(start, b, off, len)
        return b
    }

    fun getFromBufferSafeLength(key: K, b: ByteArray, off: Int, len: Int): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start, b, 0, minOf(b.size, size))
        return b
    }

    /** Note: This method assumes b can hold the size */
    fun getFromBuffer(key: K, startOff: Int, b: ByteArray): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start + startOff, b, 0, size)
        return b
    }

    fun getFromBufferSafeLength(key: K, startOff: Int, b: ByteArray): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start + startOff, b, 0, minOf(b.size, size))
        return b
    }

    /** Note: This method assumes b can hold the size */
    fun getFromBuffer(key: K, startOff: Int, b: ByteArray, off: Int, len: Int): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        getFromBuffer(start + startOff, b, off, len)
        return b
    }

    fun getFromBufferSafeLength(key: K, startOff: Int, b: ByteArray, off: Int, len: Int): ByteArray? {
        val pos = indices[key] ?: return null
        val start = (pos and 0xFFFFFFFF).toInt()
        val size = ((pos shr 32) and 0xFFFFFFFF).toInt()
        getFromBuffer(start + startOff, b, 0, minOf(b.size, size))
        return b
    }

    private fun getFromBuffer(start: Int, b: ByteArray, off: Int, len: Int) =
            buffer.copyInto(b, off, start, start + len)

    fun putIntoBuffer(key: K, b: ByteArray) {
        val pos = writeIndex
        val size = b.size
        if (buffer.size < pos + size) grow(pos + size)

        b.copyInto(buffer, pos, 0, size)

        writeIndex += size

        indices[key] = pos.toLong() or (size.toLong() shl 32)
    }

    fun putIntoBuffer(key: K, array: Array<ByteArray>) {
        var totalSize: Int = 0
        for (b in array) totalSize += b.size
        putIntoBuffer(key, array, totalSize)
    }

    fun putIntoBuffer(key: K, array: Array<ByteArray>, totalSize: Int) {
        val startPos = writeIndex
        var pos = writeIndex
        val size = totalSize+array.size+array.size
        if (buffer.size < pos + size) grow(pos + size)

        for (i in array.indices) {
            val b = array[i]
            val bSize = b.size
            buffer[pos++] = bSize.toByte()
            buffer[pos++] = bSize.shr(8).toByte()
            b.copyInto(buffer, pos, 0, bSize)
            pos += bSize
//            Boo :(
//            array[i] = null
        }

        writeIndex = pos

        indices[key] = startPos.toLong() or (size.toLong() shl 32)
    }

    fun putIntoBuffer(key: K, list: List<ByteArray>) {
        var totalSize: Int = 0
        for (b in list) totalSize += b.size
        putIntoBuffer(key, list, totalSize)
    }

    fun putIntoBuffer(key: K, list: List<ByteArray>, totalSize: Int) {
        val startPos = writeIndex
        var pos = writeIndex
        val size = totalSize+list.size+list.size
        if (buffer.size < pos + size) grow(pos + size)

        for (b in list) {
            val bSize = b.size
            buffer[pos++] = bSize.toByte()
            buffer[pos++] = bSize.shr(8).toByte()
            b.copyInto(buffer, pos, 0, bSize)
            pos += bSize
        }

        writeIndex = pos

        indices[key] = startPos.toLong() or (size.toLong() shl 32)
    }
}