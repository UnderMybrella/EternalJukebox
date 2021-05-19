package dev.eternalbox.client.jvm.magma

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.track.playback.AbstractMutableAudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.brella.kornea.io.jvm.clearSafe
import dev.brella.kornea.io.jvm.flipSafe
import dev.brella.kornea.io.jvm.rewindSafe
import java.nio.ByteBuffer

/**
 * This is a copy of [MutableAudioFrame] with a few changes
 */
class ExchangedMutableAudioFrame(format: AudioDataFormat) : MutableAudioFrame() {
    private val frameBuffer: ByteBuffer = ByteBuffer.allocateDirect(format.maximumChunkSize())

    override fun setBuffer(frameBuffer: ByteBuffer?) {
        if (this.frameBuffer.capacity() < frameBuffer?.capacity() ?: 0)
            println("Undercapacity!")

        if (frameBuffer != null && frameBuffer.hasRemaining()) {
            this.frameBuffer.clearSafe()
            this.frameBuffer.put(frameBuffer)
            this.frameBuffer.flipSafe()
        } else {
            this.frameBuffer.clearSafe()
        }
    }

    /**
     * This should be called only by the provider of a frame.
     *
     * @param buffer Buffer to copy data from into the internal buffer of this instance.
     * @param offset Offset in the buffer.
     * @param length Length of the data to copy.
     */
    override fun store(buffer: ByteArray, offset: Int, length: Int) {
        frameBuffer.clearSafe()
        frameBuffer.put(buffer, offset, length)
        frameBuffer.flipSafe()
    }

    override fun getDataLength(): Int {
        return frameBuffer.limit()
    }

    override fun getData(): ByteArray {
        val data = ByteArray(frameBuffer.limit())
        getData(data, 0)
        return data
    }

    override fun getData(buffer: ByteArray, offset: Int) {
        frameBuffer.rewindSafe()
        frameBuffer.get(buffer, offset, buffer.size)
    }

    init {
        setFormat(format)
    }
}