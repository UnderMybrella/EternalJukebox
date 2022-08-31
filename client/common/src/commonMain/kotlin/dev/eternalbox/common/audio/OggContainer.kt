package dev.eternalbox.common.audio

import dev.brella.kornea.base.common.closeAfter
import dev.eternalbox.common.audio.OggContainer.Companion.HEADER_TYPE_CONTINUED_PACKET
import dev.eternalbox.common.audio.OggContainer.Companion.HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM
import dev.eternalbox.common.audio.OggContainer.Companion.HEADER_TYPE_LAST_PAGE_OF_BITSTREAM
import dev.eternalbox.common.sumUntil
import dev.brella.kornea.errors.common.*
import dev.brella.kornea.io.common.*
import dev.brella.kornea.io.common.flow.InputFlow
import dev.brella.kornea.io.common.flow.WindowedInputFlow
import dev.brella.kornea.io.common.flow.extensions.readInt32LE
import dev.brella.kornea.io.common.flow.extensions.readInt64LE

@ExperimentalUnsignedTypes
open class OggContainer(val pages: Array<OggPage>, val dataSource: DataSource<*>) {
    interface OggPage {
        val headerType: Int
        val absoluteGranulePosition: Long
        val streamSerialNumber: Int
        val pageSequenceNumber: Int
        val crc: Int
        val lacingValues: IntArray
        val dataStart: ULong
    }

    data class GenericOggPage(override val headerType: Int, override val absoluteGranulePosition: Long, override val streamSerialNumber: Int, override val pageSequenceNumber: Int, override val crc: Int, override val lacingValues: IntArray, override val dataStart: ULong) : OggPage

    companion object {
        //OggS
        const val CAPTURE_PATTERN_BE = 0x4F676753
        const val CAPTURE_PATTERN_LE = 0x5367674F

        const val STREAM_STRUTURE_VERSION = 0x00

        const val HEADER_TYPE_CONTINUED_PACKET = 0x01
        const val HEADER_TYPE_FRESH_PACKET = HEADER_TYPE_CONTINUED_PACKET.inv()
        const val HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM = 0x02
        const val HEADER_TYPE_NOT_FIRST_PAGE_OF_BITSTREAM = HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM.inv()
        const val HEADER_TYPE_LAST_PAGE_OF_BITSTREAM = 0x04
        const val HEADER_TYPE_NOT_LAST_PAGE_OF_BITSTREAM = HEADER_TYPE_LAST_PAGE_OF_BITSTREAM.inv()

        @ExperimentalUnsignedTypes
        suspend fun unsafe(source: DataSource<*>): KorneaResult<OggContainer> {
            val flow = source.openInputFlow().getOrBreak { return it.cast() }
            return closeAfter(flow) {
                val pages: MutableList<OggPage> = ArrayList()

                var lacingSum = 0
                while (!flow.isClosed) {
                    val capturePattern = flow.readInt32LE() ?: break
                    require(capturePattern == CAPTURE_PATTERN_LE)

                    val streamStructureVersion = flow.read() ?: return@closeAfter korneaNotEnoughData()
                    require(streamStructureVersion == STREAM_STRUTURE_VERSION)

                    val headerType = flow.read() ?: return@closeAfter korneaNotEnoughData()

                    if (headerType and HEADER_TYPE_CONTINUED_PACKET != HEADER_TYPE_CONTINUED_PACKET) lacingSum = 0

                    val absoluteGranulePosition = flow.readInt64LE() ?: return@closeAfter korneaNotEnoughData()
                    val streamSerialNumber = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                    val pageSequenceNumber = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                    val crc = flow.readInt32LE() ?: return@closeAfter korneaNotEnoughData()
                    val packetCount = flow.read() ?: return@closeAfter korneaNotEnoughData()
                    val lacingValues: MutableList<Int> = ArrayList(packetCount)
                    for (i in 0 until packetCount) {
                        val read = flow.read() ?: return@closeAfter korneaNotEnoughData()
                        if (read == 255) {
                            lacingSum += 255
                        } else {
                            lacingValues.add(read + lacingSum)
                            lacingSum = 0
                        }
                    }

                    val dataStart = flow.position()

                    lacingValues.forEach { lacing ->
                        flow.skip(lacing.toULong())
                    }

                    pages.add(GenericOggPage(headerType, absoluteGranulePosition, streamSerialNumber, pageSequenceNumber, crc, lacingValues.toIntArray(), dataStart))
                }

                if (pages.isEmpty()) return@closeAfter korneaNotFound()
                KorneaResult.success(OggContainer(pages.toTypedArray(), source))
            }
        }
    }

    suspend fun openPacketInputFlow(page: OggPage, packet: Int): KorneaResult<InputFlow> = dataSource.openInputFlow().map { parent ->
        WindowedInputFlow(parent, page.dataStart + page.lacingValues.sumUntil(packet).toULong(), page.lacingValues[packet].toULong())
    }
}

val OggContainer.OggPage.isContinuedPacket: Boolean
    get() = headerType and HEADER_TYPE_CONTINUED_PACKET == HEADER_TYPE_CONTINUED_PACKET

val OggContainer.OggPage.isFreshPacket: Boolean
    get() = headerType and HEADER_TYPE_CONTINUED_PACKET != HEADER_TYPE_CONTINUED_PACKET

val OggContainer.OggPage.isFirstPageOfBitstream: Boolean
    get() = headerType and HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM == HEADER_TYPE_FIRST_PAGE_OF_BITSTREAM

val OggContainer.OggPage.isLastPageOfBitstream: Boolean
    get() = headerType and HEADER_TYPE_LAST_PAGE_OF_BITSTREAM == HEADER_TYPE_LAST_PAGE_OF_BITSTREAM

