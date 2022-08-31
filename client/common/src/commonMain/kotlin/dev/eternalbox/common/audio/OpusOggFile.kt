package dev.eternalbox.common.audio

import dev.brella.kornea.base.common.closeAfter
import dev.brella.kornea.errors.common.*
import dev.brella.kornea.io.common.*
import dev.brella.kornea.io.common.flow.*
import dev.brella.kornea.io.common.flow.extensions.readInt16LE
import dev.brella.kornea.io.common.flow.extensions.readInt32LE
import dev.brella.kornea.io.common.flow.extensions.readInt64BE
import dev.brella.kornea.toolkit.common.oneTimeMutable
import dev.brella.kornea.toolkit.common.oneTimeMutableInline

class OpusOggFile private constructor(pages: Array<OggPage>, dataSource: DataSource<*>) :
    OggContainer(pages, dataSource) {
    data class OpusHeaderPacket(
        val version: Int,
        val channelCount: Int,
        val preskip: Int,
        val inputSampleRate: Int,
        val outputGain: Int,
        val channelMappingFamily: Int
    )

    data class OpusCommentPacket(val vendor: String, val userComments: Map<String, String>)
    data class OpusAudioPacket(
        val codecLayer: CodecLayer,
        val audioBandwidth: AudioBandwidth,
        val frameDuration: FrameDuration,
        val stereo: Boolean,
        val audioFrames: Array<ByteArray>
    )

    enum class CodecLayer {
        SILK,
        HYBRID,
        CELT;
    }

    enum class AudioBandwidth(val bandwidth: Int, val effectiveSampleRate: Int) {
        NARROWBAND(4_000, 8_000),
        MEDIUMBAND(6_000, 12_000),
        WIDEBAND(8_000, 16_000),
        SUPER_WIDEBAND(12_000, 24_000),
        FULLBAND(20_000, 48_000);

        companion object {
            val NB = NARROWBAND
            val MB = MEDIUMBAND
            val WB = WIDEBAND
            val SWB = SUPER_WIDEBAND
            val FB = FULLBAND
        }
    }

    enum class FrameDuration(val ms: Double) {
        TWO_POINT_FIVE_MS(2.5),
        FIVE_MS(5.0),
        TEN_MS(10.0),
        TWENTY_MS(20.0),
        FORTY_MS(40.0),
        SIXTY_MS(60.0);
    }

    companion object {
        const val OPUS_HEADER_CAPTURE_PATTERN_BE = 0x4F70757348656164
        const val OPUS_COMMENT_CAPTURE_PATTERN_BE = 0x4F70757354616773

        const val SILK_ONLY_NARROWBAND_10MS = 0
        const val SILK_ONLY_NARROWBAND_20MS = 1
        const val SILK_ONLY_NARROWBAND_40MS = 2
        const val SILK_ONLY_NARROWBAND_60MS = 3

        const val SILK_ONLY_MEDIUMBAND_10MS = 4
        const val SILK_ONLY_MEDIUMBAND_20MS = 5
        const val SILK_ONLY_MEDIUMBAND_40MS = 6
        const val SILK_ONLY_MEDIUMBAND_60MS = 7

        const val SILK_ONLY_WIDEBAND_10MS = 8
        const val SILK_ONLY_WIDEBAND_20MS = 9
        const val SILK_ONLY_WIDEBAND_40MS = 10
        const val SILK_ONLY_WIDEBAND_60MS = 11

        const val HYBRID_SUPER_WIDEBAND_10MS = 12
        const val HYBRID_SUPER_WIDEBAND_20MS = 13

        const val HYBRID_FULLBAND_10MS = 14
        const val HYBRID_FULLBAND_20MS = 15

        const val CELT_ONLY_NARROWBAND_2MS = 16
        const val CELT_ONLY_NARROWBAND_5MS = 17
        const val CELT_ONLY_NARROWBAND_10MS = 18
        const val CELT_ONLY_NARROWBAND_20MS = 19

        const val CELT_ONLY_WIDEBAND_2MS = 20
        const val CELT_ONLY_WIDEBAND_5MS = 21
        const val CELT_ONLY_WIDEBAND_10MS = 22
        const val CELT_ONLY_WIDEBAND_20MS = 23

        const val CELT_ONLY_SUPER_WIDEBAND_2MS = 24
        const val CELT_ONLY_SUPER_WIDEBAND_5MS = 25
        const val CELT_ONLY_SUPER_WIDEBAND_10MS = 26
        const val CELT_ONLY_SUPER_WIDEBAND_20MS = 27

        const val CELT_ONLY_FULLBAND_2MS = 28
        const val CELT_ONLY_FULLBAND_5MS = 29
        const val CELT_ONLY_FULLBAND_10MS = 30
        const val CELT_ONLY_FULLBAND_20MS = 31

        val AUDIO_CONFIGURATION_MAPPING = mapOf(
            SILK_ONLY_NARROWBAND_10MS to Triple(CodecLayer.SILK, AudioBandwidth.NARROWBAND, FrameDuration.TEN_MS),
            SILK_ONLY_NARROWBAND_20MS to Triple(CodecLayer.SILK, AudioBandwidth.NARROWBAND, FrameDuration.TWENTY_MS),
            SILK_ONLY_NARROWBAND_40MS to Triple(CodecLayer.SILK, AudioBandwidth.NARROWBAND, FrameDuration.FORTY_MS),
            SILK_ONLY_NARROWBAND_60MS to Triple(CodecLayer.SILK, AudioBandwidth.NARROWBAND, FrameDuration.SIXTY_MS),

            SILK_ONLY_MEDIUMBAND_10MS to Triple(CodecLayer.SILK, AudioBandwidth.MEDIUMBAND, FrameDuration.TEN_MS),
            SILK_ONLY_MEDIUMBAND_20MS to Triple(CodecLayer.SILK, AudioBandwidth.MEDIUMBAND, FrameDuration.TWENTY_MS),
            SILK_ONLY_MEDIUMBAND_40MS to Triple(CodecLayer.SILK, AudioBandwidth.MEDIUMBAND, FrameDuration.FORTY_MS),
            SILK_ONLY_MEDIUMBAND_60MS to Triple(CodecLayer.SILK, AudioBandwidth.MEDIUMBAND, FrameDuration.SIXTY_MS),

            SILK_ONLY_WIDEBAND_10MS to Triple(CodecLayer.SILK, AudioBandwidth.WIDEBAND, FrameDuration.TEN_MS),
            SILK_ONLY_WIDEBAND_20MS to Triple(CodecLayer.SILK, AudioBandwidth.WIDEBAND, FrameDuration.TWENTY_MS),
            SILK_ONLY_WIDEBAND_40MS to Triple(CodecLayer.SILK, AudioBandwidth.WIDEBAND, FrameDuration.FORTY_MS),
            SILK_ONLY_WIDEBAND_60MS to Triple(CodecLayer.SILK, AudioBandwidth.WIDEBAND, FrameDuration.SIXTY_MS),

            HYBRID_SUPER_WIDEBAND_10MS to Triple(
                CodecLayer.HYBRID,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.TEN_MS
            ),
            HYBRID_SUPER_WIDEBAND_20MS to Triple(
                CodecLayer.HYBRID,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.TWENTY_MS
            ),

            HYBRID_FULLBAND_10MS to Triple(CodecLayer.HYBRID, AudioBandwidth.FULLBAND, FrameDuration.TEN_MS),
            HYBRID_FULLBAND_20MS to Triple(CodecLayer.HYBRID, AudioBandwidth.FULLBAND, FrameDuration.TWENTY_MS),

            CELT_ONLY_NARROWBAND_2MS to Triple(
                CodecLayer.CELT,
                AudioBandwidth.NARROWBAND,
                FrameDuration.TWO_POINT_FIVE_MS
            ),
            CELT_ONLY_NARROWBAND_5MS to Triple(CodecLayer.CELT, AudioBandwidth.NARROWBAND, FrameDuration.FIVE_MS),
            CELT_ONLY_NARROWBAND_10MS to Triple(CodecLayer.CELT, AudioBandwidth.NARROWBAND, FrameDuration.TEN_MS),
            CELT_ONLY_NARROWBAND_20MS to Triple(CodecLayer.CELT, AudioBandwidth.NARROWBAND, FrameDuration.TWENTY_MS),

            CELT_ONLY_WIDEBAND_2MS to Triple(CodecLayer.CELT, AudioBandwidth.WIDEBAND, FrameDuration.TWO_POINT_FIVE_MS),
            CELT_ONLY_WIDEBAND_5MS to Triple(CodecLayer.CELT, AudioBandwidth.WIDEBAND, FrameDuration.FIVE_MS),
            CELT_ONLY_WIDEBAND_10MS to Triple(CodecLayer.CELT, AudioBandwidth.WIDEBAND, FrameDuration.TEN_MS),
            CELT_ONLY_WIDEBAND_20MS to Triple(CodecLayer.CELT, AudioBandwidth.WIDEBAND, FrameDuration.TWENTY_MS),

            CELT_ONLY_SUPER_WIDEBAND_2MS to Triple(
                CodecLayer.CELT,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.TWO_POINT_FIVE_MS
            ),
            CELT_ONLY_SUPER_WIDEBAND_5MS to Triple(
                CodecLayer.CELT,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.FIVE_MS
            ),
            CELT_ONLY_SUPER_WIDEBAND_10MS to Triple(
                CodecLayer.CELT,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.TEN_MS
            ),
            CELT_ONLY_SUPER_WIDEBAND_20MS to Triple(
                CodecLayer.CELT,
                AudioBandwidth.SUPER_WIDEBAND,
                FrameDuration.TWENTY_MS
            ),

            CELT_ONLY_FULLBAND_2MS to Triple(CodecLayer.CELT, AudioBandwidth.FULLBAND, FrameDuration.TWO_POINT_FIVE_MS),
            CELT_ONLY_FULLBAND_5MS to Triple(CodecLayer.CELT, AudioBandwidth.FULLBAND, FrameDuration.FIVE_MS),
            CELT_ONLY_FULLBAND_10MS to Triple(CodecLayer.CELT, AudioBandwidth.FULLBAND, FrameDuration.TEN_MS),
            CELT_ONLY_FULLBAND_20MS to Triple(CodecLayer.CELT, AudioBandwidth.FULLBAND, FrameDuration.TWENTY_MS)
        )

        const val TOC_MONO = 0
        const val TOC_STEREO = 1

        const val TOC_ONE_FRAME = 0
        const val TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE = 1
        const val TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES = 2
        const val TOC_ARBITRARY_FRAMES = 3

        fun getPacketDetails(packet: ByteArray): Triple<CodecLayer, AudioBandwidth, FrameDuration> {
            val configurationByte = packet[0].toInt() and 0xFF
            val audioConfigBits = (configurationByte shr 3 and 31)

            return AUDIO_CONFIGURATION_MAPPING.getValue(audioConfigBits)
        }

        fun getPacketDuration(packet: ByteArray): Double {
            val configurationByte = packet[0].toInt() and 0xFF
            val frameBits = (configurationByte and 3)
            val audioConfigBits = (configurationByte shr 3 and 31)

            val ms = AUDIO_CONFIGURATION_MAPPING.getValue(audioConfigBits).third.ms

            when (frameBits) {
                TOC_ONE_FRAME -> return ms
                TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE -> return ms * 2
                TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES -> return ms * 2
                TOC_ARBITRARY_FRAMES -> return ms * packet[1].toInt().and(63)
                else -> return 0.0
            }
        }

        @ExperimentalStdlibApi
        @ExperimentalUnsignedTypes
        suspend fun fromOggContainer(ogg: OggContainer): KorneaResult<OpusOggFile> {
            val opus = OpusOggFile(ogg.pages, ogg.dataSource)
            opus.headerPacket = readOpusHeaderPage(ogg).getOrBreak { return it.cast() }
            opus.commentPacket = readOpusCommentPage(ogg).getOrBreak { return it.cast() }
            opus.audioPackets = readOpusAudioPages(ogg).getOrBreak { return it.cast() }
            return KorneaResult.success(opus)
        }

        @ExperimentalUnsignedTypes
        suspend fun readOpusHeaderPage(ogg: OggContainer): KorneaResult<OpusHeaderPacket> =
            ogg.openPacketInputFlow(ogg.pages[0], 0).flatMap { flow ->
                closeAfter(flow) {
                    val capturePattern = flow.readInt64BE()
                    require(capturePattern == OPUS_HEADER_CAPTURE_PATTERN_BE)

                    val version = flow.read() ?: return@closeAfter korneaNotEnoughData()
                    val channelCount = flow.read() ?: return@closeAfter korneaNotEnoughData()
                    val preskip = flow.readInt16LE() ?: return@closeAfter korneaNotEnoughData()
                    val inputSampleRate = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                    val outputGain = flow.readInt16LE() ?: return@closeAfter korneaNotEnoughData()
                    val channelMappingFamily = flow.read() ?: return@closeAfter korneaNotEnoughData()

                    KorneaResult.success(
                        OpusHeaderPacket(
                            version,
                            channelCount,
                            preskip,
                            inputSampleRate,
                            outputGain,
                            channelMappingFamily
                        )
                    )
                }
            }

        @ExperimentalUnsignedTypes
        @ExperimentalStdlibApi
        suspend fun readOpusCommentPage(ogg: OggContainer): KorneaResult<OpusCommentPacket> =
            ogg.openPacketInputFlow(ogg.pages.drop(1).first { page -> page.lacingValues.isNotEmpty() }, 0)
                .flatMap { flow ->
                    closeAfter(flow) {
                        val capturePattern = flow.readInt64BE()
                        require(capturePattern == OPUS_COMMENT_CAPTURE_PATTERN_BE)

                        val vendorStringLength = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                        val vendorStringData = ByteArray(vendorStringLength)
                        flow.readExact(vendorStringData) ?: return@closeAfter korneaNotEnoughData()
                        val vendorString = vendorStringData.decodeToString()

                        val userCommentListLength = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                        val userComments = Array(userCommentListLength) {
                            val commentLength = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                            val commentData = ByteArray(commentLength)
                            flow.readExact(commentData) ?: return@closeAfter korneaNotEnoughData()

                            commentData.decodeToString()
                        }

                        KorneaResult.success(
                            OpusCommentPacket(vendorString,
                                userComments.associate { str ->
                                    Pair(
                                        str.substringBefore('='),
                                        str.substringAfter('=')
                                    )
                                })
                        )
                    }
                }

        @ExperimentalUnsignedTypes
        suspend fun InputFlow.readFrameLengthCoding(): Int? {
            val a = read() ?: return null
            if (a < 252) return a
            return read()?.times(4)?.plus(a)
        }

        @ExperimentalUnsignedTypes
        suspend fun readOpusAudioPages(ogg: OggContainer): KorneaResult<Array<OpusAudioPacket>> {
            val audioPages: MutableList<OpusAudioPacket> = ArrayList()

            ogg.pages.drop(1)
                .dropWhile { page -> page.lacingValues.isEmpty() }
                .drop(1)
                .forEach { page ->
                    page.lacingValues.forEachIndexed { packet, packetSize ->
                        val flow = ogg.openPacketInputFlow(page, packet).getOrBreak { return it.cast() }
                        try {
                            val configurationByte = flow.read() ?: return korneaNotEnoughData()
                            val frameBits = (configurationByte and 3)
                            val isStereo = (configurationByte shr 2 and 1) == 1
                            val audioConfigBits = (configurationByte shr 3 and 31)

                            val triple = AUDIO_CONFIGURATION_MAPPING.getValue(audioConfigBits)
                            val codecLayer = triple.first
                            val audioBandwidth = triple.second
                            val frameDuration = triple.third
                            val frameLengths: IntArray
                            var paddingLength: Int? = null

                            when (frameBits) {
                                TOC_ONE_FRAME -> frameLengths = intArrayOf(packetSize - 1)
                                TOC_TWO_FRAMES_EQUAL_COMPRESSED_SIZE -> {
//                                val size: Int
//                                if (index == pages.size - 1) {
//                                    size = (page.packet.size - 1) / 2
//                                } else {
//                                    size = flow.readFrameLengthCoding() ?: return korneaNotEnoughData()
//                                }

                                    val size = (packetSize - 1) / 2
                                    frameLengths = intArrayOf(size, size)
                                }
                                TOC_TWO_FRAMES_DIFFERENT_COMPRESSED_SIZES -> {
//                                if (index == pages.size - 1) {
                                    val firstByte = flow.read() ?: return korneaNotEnoughData()
                                    if (firstByte >= 252) {
                                        val firstSize = firstByte + (flow.read() ?: return korneaNotEnoughData()) * 4

                                        frameLengths = intArrayOf(firstSize, packetSize - 3 - firstSize)
                                    } else {
                                        frameLengths = intArrayOf(firstByte, packetSize - 2 - firstByte)
                                    }
//                                } else {
//                                    frameLengths = IntArray(2) {
//                                        flow.readFrameLengthCoding() ?: return korneaNotEnoughData()
//                                    }
//                                }
                                }
                                TOC_ARBITRARY_FRAMES -> {
                                    val frameCountByte = flow.read() ?: return korneaNotEnoughData()

                                    val frameCount = frameCountByte and 63
                                    val hasPadding = (frameCountByte shr 6 and 1) == 1
                                    val isVBR = (frameCountByte shr 7 and 1) == 1

                                    if (hasPadding) {
                                        paddingLength = flow.read() ?: return korneaNotEnoughData()
                                        if (paddingLength == 255) {
                                            paddingLength = 255 + (flow.read() ?: return korneaNotEnoughData())
                                        }
                                    }

                                    if (isVBR) {
//                                    if (index == pages.size - 1) {
                                        val vbrLengths = IntArray(frameCount) { i ->
                                            if (i == frameCount - 1) 0
                                            else flow.readFrameLengthCoding() ?: return korneaNotEnoughData()
                                        }

                                        vbrLengths[vbrLengths.size - 1] = packetSize - 2 -
                                                (paddingLength ?: 0) - vbrLengths.sum()

                                        frameLengths = vbrLengths
//                                    } else {
//                                        frameLengths = IntArray(frameCount) {
//                                            flow.readFrameLengthCoding() ?: return korneaNotEnoughData()
//                                        }
//                                    }
                                    } else {
//                                    if (index == pages.size - 1) {
                                        val frameSize = (packetSize - 2 - (paddingLength ?: 0)) / frameCount
                                        frameLengths = IntArray(frameCount) { frameSize }
//                                    } else {
//                                        val frameSize: Int = flow.readFrameLengthCoding()
//                                                ?: return korneaNotEnoughData()
//
//                                        frameLengths = IntArray(frameCount) { frameSize }
//                                    }
                                    }
                                }
                                else -> error("Impossible value: $frameBits")
                            }

                            val audioFrames = Array(frameLengths.size) { i ->
                                val buffer = ByteArray(frameLengths[i])
                                flow.readExact(buffer)
                                    ?: return korneaNotEnoughData("Failed to read ${frameLengths[i]} bytes")
                                buffer
                            }
                            audioPages.add(
                                OpusAudioPacket(
                                    codecLayer,
                                    audioBandwidth,
                                    frameDuration,
                                    isStereo,
                                    audioFrames
                                )
                            )

                            paddingLength?.toULong()?.let { flow.skip(it) }
                        } finally {
                            flow.close()
                        }
                    }
                }

            return KorneaResult.success(audioPages.toTypedArray())
        }
    }

    var headerPacket: OpusHeaderPacket by oneTimeMutableInline()
    var commentPacket: OpusCommentPacket by oneTimeMutableInline()
    var audioPackets: Array<OpusAudioPacket> by oneTimeMutableInline()
}

@ExperimentalUnsignedTypes
@ExperimentalStdlibApi
suspend fun OggContainer.asOpus(): KorneaResult<OpusOggFile> = OpusOggFile.fromOggContainer(this)