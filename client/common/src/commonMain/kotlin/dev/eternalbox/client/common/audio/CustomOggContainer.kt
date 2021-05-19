package dev.eternalbox.client.common.audio

import dev.brella.kornea.annotations.ExperimentalKorneaIO
import dev.eternalbox.client.common.BufferWrapOutputFlow
import dev.eternalbox.client.common.audio.OggContainer.Companion.CAPTURE_PATTERN_LE
import dev.eternalbox.client.common.audio.OggContainer.Companion.HEADER_TYPE_CONTINUED_PACKET
import dev.eternalbox.client.common.audio.OggContainer.Companion.HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM
import dev.eternalbox.client.common.audio.OggContainer.Companion.HEADER_TYPE_FRESH_PACKET
import dev.eternalbox.client.common.audio.OggContainer.Companion.HEADER_TYPE_LAST_PAGE_OF_BITSTREAM
import dev.eternalbox.client.common.audio.OggContainer.Companion.STREAM_STRUTURE_VERSION
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_FULLBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_FULLBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_FULLBAND_2MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_FULLBAND_5MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_NARROWBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_NARROWBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_NARROWBAND_2MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_NARROWBAND_5MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_SUPER_WIDEBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_SUPER_WIDEBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_SUPER_WIDEBAND_2MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_SUPER_WIDEBAND_5MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_WIDEBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_WIDEBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_WIDEBAND_2MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.CELT_ONLY_WIDEBAND_5MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.HYBRID_FULLBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.HYBRID_FULLBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.HYBRID_SUPER_WIDEBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.HYBRID_SUPER_WIDEBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.OPUS_COMMENT_CAPTURE_PATTERN_BE
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.OPUS_HEADER_CAPTURE_PATTERN_BE
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_MEDIUMBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_MEDIUMBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_MEDIUMBAND_40MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_MEDIUMBAND_60MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_NARROWBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_NARROWBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_NARROWBAND_40MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_NARROWBAND_60MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_WIDEBAND_10MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_WIDEBAND_20MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_WIDEBAND_40MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.SILK_ONLY_WIDEBAND_60MS
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_ARBITRARY_FRAMES
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_MONO
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_ONE_FRAME
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_STEREO
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES
import dev.eternalbox.client.common.audio.OpusOggFile.Companion.TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import dev.brella.kornea.io.common.*
import dev.brella.kornea.io.common.flow.BinaryOutputFlow
import dev.brella.kornea.io.common.flow.OutputFlow
import dev.brella.kornea.io.common.flow.extensions.writeInt16LE
import dev.brella.kornea.io.common.flow.extensions.writeInt32LE
import dev.brella.kornea.io.common.flow.extensions.writeInt64BE
import dev.brella.kornea.io.common.flow.extensions.writeInt64LE
import dev.brella.kornea.toolkit.common.use
import kotlin.IllegalArgumentException

@ExperimentalUnsignedTypes
@ExperimentalKorneaIO
class CustomOggContainer {
    var streamSerialNumber: Int = 0
    var granulePosition: Long = 0L
    var pageSequenceNumber: Int = 0
    private var streamJob: Job? = null
    private var hasSentFirst: Boolean = false

    val pages: Channel<CustomOggPage> = Channel(Channel.UNLIMITED)

    suspend fun addSimplePage(packet: ByteArray) {
        val page = CustomOggPage(pageSequenceNumber++)
        page.streamSerialNumber = streamSerialNumber
        page.absoluteGranulePosition = granulePosition
        if (!hasSentFirst) {
            page.setFirstPageOfBitstream()
            hasSentFirst = true
        }
        page.addPageSegment(packet)

        pages.send(page)
    }

    suspend fun newPage(init: suspend CustomOggPage.() -> Unit) {
        val page = CustomOggPage(pageSequenceNumber++)
        page.streamSerialNumber = streamSerialNumber
        page.absoluteGranulePosition = granulePosition

        if (!hasSentFirst) {
            page.setFirstPageOfBitstream()
            hasSentFirst = true
        }

        page.init()
        pages.send(page)
    }

    @ExperimentalCoroutinesApi
    fun CoroutineScope.stream(output: OutputFlow): Job {
        streamJob?.cancel()
        val job = launch {
            while (isActive && !pages.isClosedForReceive) {
                val page = pages.receive()
                output.write(page.getPacket())
                if (page.headerType and HEADER_TYPE_LAST_PAGE_OF_BITSTREAM == HEADER_TYPE_LAST_PAGE_OF_BITSTREAM) break
                yield()
            }
        }
        streamJob = job
        return job
    }

    @ExperimentalCoroutinesApi
    suspend fun writeTo(output: OutputFlow) {
        pages.close()

        var buffer = pages.receiveOrNull()
        buffer?.setFirstPageOfBitstream()
        while (buffer != null && !pages.isClosedForReceive) {
            output.write(buffer.getPacket())
            buffer = pages.receiveOrNull()
        }

        buffer?.setLastPageOfBitstream()
        if (buffer != null) {
            output.write(buffer.getPacket())
        }

        pages.close()
    }
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
open class CustomOggPage(val pageSequenceNumber: Int) {
    companion object {
        const val ABSOLUTE_GRANULE_POSITION_NO_PACKETS_FINISH = -1L

        const val PAGE_CHECKSUM_GENERATOR_POLYNOMIAL = 0x04C11DB7L

        const val MAXIMUM_PAGE_SIZE = 65307

//        suspend operator fun invoke(pageSequenceNumber: Int): CustomOggPage {
//            val pool = AsyncFileDataPool.open(Path.of("packet.dat"), create = true, deleteOnClose = true)
//            val out = pool.openOutputFlow().get()
//
//            return CustomOggPage(pageSequenceNumber, pool, out)
//        }

        val CRC_TABLE: IntArray by lazy {
            val table = IntArray(256)

            val topbit = 1L shl 31

            var crc = topbit

            var i = 1

            do {
                if (crc and topbit == topbit) {
                    crc = (crc shl 1) xor PAGE_CHECKSUM_GENERATOR_POLYNOMIAL
                } else {
                    crc = crc shl 1
                }

                for (j in 0 until i) {
                    table[i + j] = (crc xor table[j].toLong()).toInt()
                }

                i = i shl 1
            } while (i < 256)

//            table.toList().chunked(4).forEach { chunk -> println(chunk.joinToString { crc -> "0x${crc.toLong().and(0xFFFFFFFF).toString(16).padStart(8, '0')}" }) }

            table
        }
    }

    var headerType: Int = 0x00
    var absoluteGranulePosition: Long = ABSOLUTE_GRANULE_POSITION_NO_PACKETS_FINISH
    var streamSerialNumber: Int = 0

    val lacingValues: MutableList<Int> = ArrayList()
    val segments: MutableList<ByteArray> = ArrayList()

    fun setFreshPacket() {
        headerType = headerType and HEADER_TYPE_FRESH_PACKET
    }

    fun setContinuedPacket() {
        headerType = headerType or HEADER_TYPE_CONTINUED_PACKET
    }

    fun setFirstPageOfBitstream() {
        headerType = headerType or HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM
    }

    fun setLastPageOfBitstream() {
        headerType = headerType or HEADER_TYPE_LAST_PAGE_OF_BITSTREAM
    }

    fun addPageSegment(segment: ByteArray) {
        require(lacingValues.lastOrNull() != 0xFF)
        require(lacingValues.size < (254 - (segment.size / 255))) {
            ">:("
        }

        if (segment.size < 254) {
            segments.add(segment)
            lacingValues.add(segment.size)
        } else {
            repeat(segment.size / 0xFF) { lacingValues.add(0xFF) }
            lacingValues.add(segment.size % 0xFF)

            segments.add(segment)
        }
    }

    suspend fun addOpusAudio(init: suspend CustomOpusAudioPacket.() -> Unit) {
        val packet = CustomOpusAudioPacket()
        packet.init()
        addPageSegment(packet.calculateOpusPacket())
    }

    open suspend fun getPacket(): ByteArray {
        val packet = ByteArray(27 + lacingValues.size + segments.sumBy { it.size })
        val out = BufferWrapOutputFlow(packet)

        out.writeInt32LE(CAPTURE_PATTERN_LE)
        out.write(STREAM_STRUTURE_VERSION)
        out.write(headerType)
        out.writeInt64LE(absoluteGranulePosition)
        out.writeInt32LE(streamSerialNumber)
        out.writeInt32LE(pageSequenceNumber)
        out.writeInt32LE(0) //CRC
        out.write(lacingValues.size)
        lacingValues.forEach { lace -> out.write(lace) }
        segments.forEach { array -> out.write(array) }

        //Calculate CRC-32

        var crc = 0x00000000L

        packet.forEach { byte ->
//            val nLookupIndex = (crc shr 24) xor (byte.toLong() and 0xFF)//(crc xor byte.toLong().and(0xFF)) and 0xFF
//            crc = (crc shr 8) xor CRC_TABLE[nLookupIndex.toInt() and 0xFF].toLong()
            crc = (crc shl 8) xor CRC_TABLE[(byte.toLong().and(0xFF) xor (crc shr 24)).toInt() and 0xFF].toLong()
        }

//        crc = crc.inv()
        out.pos = 22
        out.writeInt32LE(crc)

        return packet
    }
}

open class CustomOpusAudioPacket {
    var codecLayer: OpusOggFile.CodecLayer? = null
    var audioBandwidth: OpusOggFile.AudioBandwidth? = null
    var frameDuration: OpusOggFile.FrameDuration? = null

    var isStereo: Boolean = false
    val frames: MutableList<ByteArray> = ArrayList()

    fun config(codecLayer: OpusOggFile.CodecLayer, audioBandwidth: OpusOggFile.AudioBandwidth, frameDuration: OpusOggFile.FrameDuration) {
        this.codecLayer = codecLayer
        this.audioBandwidth = audioBandwidth
        this.frameDuration = frameDuration
    }

    fun addFrame(frame: ByteArray) {
        frames.add(frame)
    }

    suspend fun calculateOpusPacket(): ByteArray {
        val codecLayer = requireNotNull(codecLayer)
        val audioBandwidth = requireNotNull(audioBandwidth)
        val frameDuration = requireNotNull(frameDuration)

        val config = when (codecLayer) {
            OpusOggFile.CodecLayer.SILK -> when (audioBandwidth) {
                OpusOggFile.AudioBandwidth.NARROWBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TEN_MS -> SILK_ONLY_NARROWBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> SILK_ONLY_NARROWBAND_20MS
                    OpusOggFile.FrameDuration.FORTY_MS -> SILK_ONLY_NARROWBAND_40MS
                    OpusOggFile.FrameDuration.SIXTY_MS -> SILK_ONLY_NARROWBAND_60MS
                    else -> throw IllegalArgumentException("Illegal frame duration for SILK_ONLY_NARROWBAND (Only accepts 10/20/40/60 ms)")
                }
                OpusOggFile.AudioBandwidth.MEDIUMBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TEN_MS -> SILK_ONLY_MEDIUMBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> SILK_ONLY_MEDIUMBAND_20MS
                    OpusOggFile.FrameDuration.FORTY_MS -> SILK_ONLY_MEDIUMBAND_40MS
                    OpusOggFile.FrameDuration.SIXTY_MS -> SILK_ONLY_MEDIUMBAND_60MS
                    else -> throw IllegalArgumentException("Illegal frame duration for SILK_ONLY_MEDIUMBAND (Only accepts 10/20/40/60 ms)")
                }
                OpusOggFile.AudioBandwidth.WIDEBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TEN_MS -> SILK_ONLY_WIDEBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> SILK_ONLY_WIDEBAND_20MS
                    OpusOggFile.FrameDuration.FORTY_MS -> SILK_ONLY_WIDEBAND_40MS
                    OpusOggFile.FrameDuration.SIXTY_MS -> SILK_ONLY_WIDEBAND_60MS
                    else -> throw IllegalArgumentException("Illegal frame duration for SILK_ONLY_WIDEBAND (Only accepts 10/20/40/60 ms)")
                }
                else -> throw IllegalArgumentException("Illegal bandwidth for SILK_ONLY (Only accepts narrowband/medium-band/wideband")
            }
            OpusOggFile.CodecLayer.HYBRID -> when (audioBandwidth) {
                OpusOggFile.AudioBandwidth.SUPER_WIDEBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TEN_MS -> HYBRID_SUPER_WIDEBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> HYBRID_SUPER_WIDEBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for HYBRID_SUPER_WIDEBAND (Only accepts 10/20 ms)")
                }
                OpusOggFile.AudioBandwidth.FULLBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TEN_MS -> HYBRID_FULLBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> HYBRID_FULLBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for HYBRID_FULLBAND (Only accepts 10/20 ms)")
                }
                else -> throw IllegalArgumentException("Illegal bandwidth for HYBRID (Only accepts super wideband/fullband)")
            }
            OpusOggFile.CodecLayer.CELT -> when (audioBandwidth) {
                OpusOggFile.AudioBandwidth.NARROWBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TWO_POINT_FIVE_MS -> CELT_ONLY_NARROWBAND_2MS
                    OpusOggFile.FrameDuration.FIVE_MS -> CELT_ONLY_NARROWBAND_5MS
                    OpusOggFile.FrameDuration.TEN_MS -> CELT_ONLY_NARROWBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> CELT_ONLY_NARROWBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for CELT_ONLY_NARROWBAND (Only accepts 2.5/5/10/20 ms)")
                }
                OpusOggFile.AudioBandwidth.WIDEBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TWO_POINT_FIVE_MS -> CELT_ONLY_WIDEBAND_2MS
                    OpusOggFile.FrameDuration.FIVE_MS -> CELT_ONLY_WIDEBAND_5MS
                    OpusOggFile.FrameDuration.TEN_MS -> CELT_ONLY_WIDEBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> CELT_ONLY_WIDEBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for CELT_ONLY_WIDEBAND (Only accepts 2.5/5/10/20 ms)")
                }
                OpusOggFile.AudioBandwidth.SUPER_WIDEBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TWO_POINT_FIVE_MS -> CELT_ONLY_SUPER_WIDEBAND_2MS
                    OpusOggFile.FrameDuration.FIVE_MS -> CELT_ONLY_SUPER_WIDEBAND_5MS
                    OpusOggFile.FrameDuration.TEN_MS -> CELT_ONLY_SUPER_WIDEBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> CELT_ONLY_SUPER_WIDEBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for CELT_ONLY_SUPER_WIDEBAND (Only accepts 2.5/5/10/20 ms)")
                }
                OpusOggFile.AudioBandwidth.FULLBAND -> when (frameDuration) {
                    OpusOggFile.FrameDuration.TWO_POINT_FIVE_MS -> CELT_ONLY_FULLBAND_2MS
                    OpusOggFile.FrameDuration.FIVE_MS -> CELT_ONLY_FULLBAND_5MS
                    OpusOggFile.FrameDuration.TEN_MS -> CELT_ONLY_FULLBAND_10MS
                    OpusOggFile.FrameDuration.TWENTY_MS -> CELT_ONLY_FULLBAND_20MS
                    else -> throw IllegalArgumentException("Illegal frame duration for CELT_ONLY_FULLBAND (Only accepts 2.5/5/10/20 ms)")
                }
                else -> throw IllegalArgumentException("Illegal bandwidth for CELT_ONLY (Only accepts narrowband/wideband/super wideband/fullband)")
            }
        }
        val stereoBit = if (isStereo) TOC_STEREO else TOC_MONO
        val frameBits = if (frames.size == 1)
            TOC_ONE_FRAME
        else if (frames.size == 2 && frames[0].size == frames[1].size)
            TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE
        else if (frames.size == 2)
            TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES
        else
            TOC_ARBITRARY_FRAMES

//            val configByte = config or
//                    (stereoBit shl 5) or
//                    (frameBits shl 6)

        val configByte = frameBits or (stereoBit shl 2) or (config shl 3)

        val frameData = BinaryOutputFlow()
        frameData.write(configByte)

        when (frameBits) {
            TOC_ONE_FRAME -> frameData.write(frames[0])
            TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE -> {
                frameData.write(frames[0])
                frameData.write(frames[1])
            }
            TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES -> {
                val firstFrame = frames[0]
                if (firstFrame.size < 252) {
                    frameData.write(firstFrame.size)
                    frameData.write(firstFrame)
                } else if (firstFrame.size <= 1275) {
                    val secondByte = (firstFrame.size - 252) / 4
                    val firstByte = 252 + (firstFrame.size % 4)

//                        println("${firstByte+(secondByte*4)}==${firstFrame.size}")

                    frameData.write(firstByte)
                    frameData.write(secondByte)
                    frameData.write(firstFrame)
                    frameData.write(frames[1])
                } else {
                    throw IllegalArgumentException("Frame too big")
                }
            }
            else -> {
                val vbr = frames.distinctBy { it.size }.size > 1
                require(frames.size < 48)
                require(frames.count() * frameDuration.ms <= 120.0)

//                    frameData.write((if (vbr) 1 else 0) or (frames.size shl 2))
                frameData.write((frames.size and 63) or ((if (vbr) 1 else 0) shl 2))
                //Don't do padding right now that's LAME
                if (vbr) {
                    frames.dropLast(1).forEach { frame ->
                        if (frame.size < 252) {
                            frameData.write(frame.size)
//                            frameData.write(frame)
                        } else if (frame.size <= 1275) {
                            val secondByte = (frame.size - 252) / 4
                            val firstByte = 252 + (frame.size % 4)

//                                println("${firstByte+(secondByte*4)}==${frame.size}")

                            frameData.write(firstByte)
                            frameData.write(secondByte)
//                            frameData.write(frame)
                        } else {
                            throw IllegalArgumentException("Frame too big")
                        }
                    }
                }

                frames.forEach { frameData.write(it) }
            }
        }

        return frameData.getData()
    }
}

class CustomOpusHeaderPacketBuilder {
    companion object {
        const val DEFAULT_VERSION = 1
        const val DEFAULT_CHANNEL_COUNT = 2
        const val DEFAULT_PRESKIP = 3840
        const val DEFAULT_INPUT_SAMPLE_RATE = 48_000
        const val DEFAULT_OUTPUT_GAIN = 0
        const val DEFAULT_CHANNEL_MAPPING_FAMILY = 0
    }

    var version: Int = DEFAULT_VERSION
    var channelCount: Int = DEFAULT_CHANNEL_COUNT
    var preskip: Int = DEFAULT_PRESKIP
    var inputSampleRate: Int = DEFAULT_INPUT_SAMPLE_RATE
    var outputGain: Int = DEFAULT_OUTPUT_GAIN

    //TODO: Support this properly
    var channelMappingFamily: Int = DEFAULT_CHANNEL_MAPPING_FAMILY

    @ExperimentalUnsignedTypes
    suspend fun buildPacket(): ByteArray {
        val packet = BinaryOutputFlow()
        packet.writeInt64BE(OPUS_HEADER_CAPTURE_PATTERN_BE)
        packet.write(version)
        packet.write(channelCount)
        packet.writeInt16LE(preskip)
        packet.writeInt32LE(inputSampleRate)
        packet.writeInt16LE(outputGain)
        packet.write(channelMappingFamily)

        return packet.getData()
    }
}

class CustomOpusCommentPacketBuilder {
    var vendorString = "libopus"
    val userComments: MutableMap<String, String> = HashMap()

    fun comment(key: String, value: String) {
        userComments[key] = value
    }

    @ExperimentalUnsignedTypes
    @ExperimentalStdlibApi
    suspend fun buildPacket(): ByteArray {
        val packet = BinaryOutputFlow()
        packet.writeInt64BE(OPUS_COMMENT_CAPTURE_PATTERN_BE)
        val vendorBytes = vendorString.encodeToByteArray()
        packet.writeInt32LE(vendorBytes.size)
        packet.write(vendorBytes)

        val comments = userComments.entries

        packet.writeInt32LE(comments.size)

        comments.forEach { (k, v) ->
            val comment = "$k=$v".encodeToByteArray()
            packet.writeInt32LE(comment.size)
            packet.write(comment)
        }

        return packet.getData()
    }
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
suspend inline fun OutputFlow.writeOggContainer(closeOnFinish: Boolean = true, init: CustomOggContainer.() -> Unit): CustomOggContainer {
    val ogg = CustomOggContainer()
    ogg.init()
    if (closeOnFinish) use { ogg.writeTo(it) }
    else ogg.writeTo(this)
    return ogg
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
fun CustomOggContainer.stream(coroutineScope: CoroutineScope, output: OutputFlow) = coroutineScope.stream(output)

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
suspend inline fun OutputFlow.streamOggContainer(coroutineScope: CoroutineScope, closeOnFinish: Boolean = true, init: CustomOggContainer.() -> Unit): CustomOggContainer {
    try {
        val ogg = CustomOggContainer()
        val job = ogg.stream(coroutineScope, this)
        ogg.init()
        job.join()
        return ogg
    } finally {
        if (closeOnFinish) close()
    }
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
inline fun customOggContainer(init: CustomOggContainer.() -> Unit): CustomOggContainer {
    val ogg = CustomOggContainer()
    ogg.init()
    return ogg
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
suspend fun CustomOggContainer.addOpusHeaderPage(init: CustomOpusHeaderPacketBuilder.() -> Unit) {
    val packet = CustomOpusHeaderPacketBuilder()
    packet.init()
    addSimplePage(packet.buildPacket())
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
suspend fun CustomOggContainer.addOpusHeaderPage(version: Int? = null, channelCount: Int? = null, preskip: Int? = null, inputSampleRate: Int? = null, outputGain: Int? = null, channelMappingFamily: Int? = null) {
    val packet = CustomOpusHeaderPacketBuilder()
    if (version != null) packet.version = version
    if (channelCount != null) packet.channelCount = channelCount
    if (preskip != null) packet.preskip = preskip
    if (inputSampleRate != null) packet.inputSampleRate = inputSampleRate
    if (outputGain != null) packet.outputGain = outputGain
    if (channelMappingFamily != null) packet.channelMappingFamily = packet.channelMappingFamily
    addSimplePage(packet.buildPacket())
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
suspend fun CustomOggContainer.addOpusCommentPage(init: CustomOpusCommentPacketBuilder.() -> Unit) {
    val packet = CustomOpusCommentPacketBuilder()
    packet.init()
    addSimplePage(packet.buildPacket())
}

@ExperimentalKorneaIO
@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
suspend fun CustomOggContainer.addOpusCommentPage(vendorString: String? = null, vararg comments: Pair<String, String>) {
    val packet = CustomOpusCommentPacketBuilder()
    if (vendorString != null) packet.vendorString = vendorString
    packet.userComments.putAll(comments)
    addSimplePage(packet.buildPacket())
}

const val OPUS_SAMPLES_FOR_20_MS = 960
const val OPUS_SAMPLES_PER_MS = OPUS_SAMPLES_FOR_20_MS / 20

@ExperimentalUnsignedTypes
@ExperimentalKorneaIO
suspend fun CustomOggContainer.addOpusAudioPackets(vararg packets: ByteArray, isLastPacket: Boolean = false) {
    val duration = (packets.sumByDouble(OpusOggFile.Companion::getPacketDuration) * OPUS_SAMPLES_PER_MS).toInt()
    granulePosition += duration

    val page = CustomOggPage(pageSequenceNumber++)
    page.streamSerialNumber = streamSerialNumber
    page.absoluteGranulePosition = granulePosition
    if (isLastPacket) page.setLastPageOfBitstream()
    packets.forEach(page::addPageSegment)

    pages.send(page)
}

@Suppress("DuplicatedCode")
@ExperimentalUnsignedTypes
@ExperimentalKorneaIO
suspend fun CustomOggContainer.addOpusAudioPackets(packets: List<ByteArray>, isLastPacket: Boolean = false) {
    val duration = (packets.sumByDouble(OpusOggFile.Companion::getPacketDuration) * OPUS_SAMPLES_PER_MS).toInt()
    granulePosition += duration

    val page = CustomOggPage(pageSequenceNumber++)
    page.streamSerialNumber = streamSerialNumber
    page.absoluteGranulePosition = granulePosition
    if (isLastPacket) page.setLastPageOfBitstream()
    packets.forEach(page::addPageSegment)

    pages.send(page)
}