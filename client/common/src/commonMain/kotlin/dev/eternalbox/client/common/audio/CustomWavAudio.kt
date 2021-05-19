package dev.eternalbox.client.common.audio

import dev.brella.kornea.errors.common.*
import dev.brella.kornea.io.common.*
import dev.brella.kornea.io.common.flow.OutputFlow
import dev.brella.kornea.io.common.flow.extensions.copyTo
import dev.brella.kornea.io.common.flow.extensions.writeInt16BE
import dev.brella.kornea.io.common.flow.extensions.writeInt16LE
import dev.brella.kornea.io.common.flow.extensions.writeInt32LE
import dev.brella.kornea.toolkit.common.DataCloseable
import dev.brella.kornea.toolkit.common.oneTimeMutable
import dev.brella.kornea.toolkit.common.use

//TODO: Fix terminology
class CustomWavAudio private constructor(val pcmBuffer: DataPool<*, *>) : DataCloseable {
    companion object {
        val MAGIC_NUMBER_LE = 0x46464952
        val TYPE_MAGIC_NUMBER_LE = 0x45564157
        val FORMAT_CHUNK_MAGIC_NUMBER_LE = 0x20746D66
        val DATA_CHUNK_MAGIC_NUMBER_LE = 0x61746164

        suspend operator fun invoke(): KorneaResult<CustomWavAudio> = invoke(BinaryDataPool())
        suspend operator fun invoke(pcmBuffer: DataPool<*, *>): KorneaResult<CustomWavAudio> = CustomWavAudio(pcmBuffer).init()
    }

    var numberOfChannels: Int = 0
    var sampleRate: Int = 44100
    var isLittleEndian: Boolean = true

    @ExperimentalUnsignedTypes
    private var pcmBufferOutput: OutputFlow by oneTimeMutable()

    override val isClosed: Boolean
        get() = pcmBuffer.isClosed

    @ExperimentalUnsignedTypes
    suspend fun addSamples(array: ShortArray) {
        array.forEach { item ->
            if (isLittleEndian) {
                pcmBufferOutput.writeInt16LE(item)
            } else {
                pcmBufferOutput.writeInt16BE(item)
            }
        }
    }

    suspend fun addSamples(list: List<Short>) {
        list.forEach { item -> pcmBufferOutput.writeInt16LE(item) }
    }

    suspend fun addBlock(block: ByteArray) {
        require(block.size % 2 == 0)
        pcmBufferOutput.write(block)
    }

    @ExperimentalUnsignedTypes
    suspend fun write(out: OutputFlow): KorneaResult<OutputFlow> {
        val pcmInput = pcmBuffer.openInputFlow().getOrBreak { return it.cast() }
        val sampleDataSize = pcmInput.size()?.toLong() ?: return KorneaResult.empty()

        out.writeInt32LE(MAGIC_NUMBER_LE)                                //Marks the file as a riff file. Characters are each 1 byte long.
        out.writeInt32LE(sampleDataSize + 44)                       //Size of the overall file - 8 bytes, in bytes (32-bit integer). Typically, you'd fill this in after creation.
        out.writeInt32LE(TYPE_MAGIC_NUMBER_LE)                           //File Type Header. For our purposes, it always equals "WAVE".
        out.writeInt32LE(FORMAT_CHUNK_MAGIC_NUMBER_LE)                   //Format chunk marker. Includes trailing null
        out.writeInt32LE(16)                                        //Length of format data as listed above
        out.writeInt16LE(1)                                         //Type of format (1 is PCM) - 2 byte integer
        out.writeInt16LE(numberOfChannels)                               //Number of Channels - 2 byte integer
        out.writeInt32LE(sampleRate)                                     //Sample Rate - 32 byte integer. Common values are 44100 (CD), 48000 (DAT). Sample Rate = Number of Samples per second, or Hertz
        out.writeInt32LE((sampleRate * 16 * numberOfChannels) / 8)  //(Sample Rate * BitsPerSample * Channels) / 8.
        out.writeInt16LE((16 * numberOfChannels) / 8)               //(BitsPerSample * Channels) / 8. 1 - 8 bit mono, 2 - 8 bit stereo / 16 bit mono, 4 - 16 bit stereo
        out.writeInt16LE(16)                                        //Bits per sample
        out.writeInt32LE(DATA_CHUNK_MAGIC_NUMBER_LE)                     //"data" chunk header. Marks the beginning of the data section.
        out.writeInt32LE(sampleDataSize)                                 //Size of the data section.

        pcmInput.use { sin -> sin.copyTo(out) }

        close()

        return KorneaResult.success(out)
    }

    @ExperimentalUnsignedTypes
    override suspend fun close() {
        pcmBuffer.close()
        pcmBufferOutput.close()
    }

    suspend fun init(): KorneaResult<CustomWavAudio> {
        pcmBufferOutput = pcmBuffer.openOutputFlow().getOrBreak { return it.cast() }
        return KorneaResult.success(this)
    }
}

@ExperimentalUnsignedTypes
suspend inline fun wavAudio(block: CustomWavAudio.() -> Unit): KorneaResult<CustomWavAudio> {
    val wav = CustomWavAudio().getOrBreak { return it.cast() }
    wav.block()
    return KorneaResult.success(wav)
}

@ExperimentalUnsignedTypes
suspend inline fun OutputFlow.writeWavAudio(block: CustomWavAudio.() -> Unit): KorneaResult<OutputFlow> =
        CustomWavAudio().flatMap { wav ->
            wav.block()
            wav.write(this)
        }