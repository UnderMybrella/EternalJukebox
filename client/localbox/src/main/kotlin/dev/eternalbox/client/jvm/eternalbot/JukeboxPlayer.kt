package dev.eternalbox.client.jvm.eternalbot

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormatTools
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.format.transcoder.OpusChunkDecoder
import dev.brella.kornea.io.jvm.files.AsyncFileOutputFlow
import dev.brella.kornea.toolkit.common.use
import dev.eternalbox.common.audio.CustomOggContainer
import dev.eternalbox.common.audio.OpusOggFile
import dev.eternalbox.common.audio.addOpusAudioPackets
import dev.eternalbox.common.audio.addOpusHeaderPage
import dev.eternalbox.common.audio.stream
import dev.eternalbox.common.jukebox.JukeboxBeatAnalysis
import dev.eternalbox.common.jukebox.JukeboxTrack
import dev.eternalbox.client.jvm.eternalbot.EternalboxTerminalClient.Companion.START_INDEX
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.ExperimentalTime

class JukeboxPlayer(val jukeboxTrack: JukeboxTrack, val beatMap: Map<Int, Array<ByteArray>>) {
    companion object {
        var LAST_INSTANCE: JukeboxPlayer? = null

        const val CHANNEL_CAPACITY = 8
    }

    val dispatcher = Executors.newSingleThreadExecutor { run -> Thread(run, "Jukebox") }.asCoroutineDispatcher()

    val sourceFormat = StandardAudioDataFormats.DISCORD_OPUS
    val pcmFormat = StandardAudioDataFormats.DISCORD_PCM_S16_LE
    val jdkFormat = AudioDataFormatTools.toAudioFormat(pcmFormat)
    val line = AudioSystem.getSourceDataLine(jdkFormat)

    //    val decoder = OpusDecoder(pcmFormat.sampleRate, pcmFormat.channelCount)
    val decoder = OpusChunkDecoder(pcmFormat)
    val pcmBuffer = ByteBuffer.allocateDirect(pcmFormat.maximumChunkSize()).asShortBuffer()

    private val delayTime = pcmFormat.frameDuration() / 2

    //    private val returnFramesToRemixer = Channel<Array<ByteArray>>(CHANNEL_CAPACITY)
    private val sendFramesToDecoder = Channel<Array<ByteArray>>(CHANNEL_CAPACITY)
    private val returnFramesToDecoder = Channel<Array<ByteArray>>(CHANNEL_CAPACITY)
    private val sendFramesToPlayer = Channel<Array<ByteArray>>(CHANNEL_CAPACITY)

    var remixingJob: Job? = null
    var encodingJob: Job? = null
    var playerJob: Job? = null

    private fun flushFrames() {
        var tmp = sendFramesToPlayer.poll()
        while (tmp != null) {
            returnFramesToDecoder.offer(tmp)
            tmp = sendFramesToPlayer.poll()
        }

//        var tmp_ = sendFramesToEncoder.poll()
//        while (tmp_ != null) {
//            returnFramesToRemixer.offer(tmp_)
//            tmp_ = sendFramesToEncoder.poll()
//        }
    }

    @ExperimentalTime
    fun launch(scope: CoroutineScope) {
        flushFrames()

        remixingJob?.cancel()
        encodingJob?.cancel()
        playerJob?.cancel()

/*        remixingJob = scope.launch {
            val positions = IntArray(100) //This should be fine
            val sizes = IntArray(100)
            var frameCount: Int
            while (isActive) {
                jukeboxTrack.beats.indices.forEach { i ->
                    frameCount = beatMap.getSubBufferPositions(i, positions, sizes)
                    repeat(frameCount) { f ->
                        val frameBuffer = returnFramesToRemixer.receive()
                        frameBuffer.clear()
                        beatMap.getFromBuffer(positions[f], sizes[f], frameBuffer)
                        frameBuffer.flip()
                        sendFramesToEncoder.send(frameBuffer)

                        yield()
                    }
                }

                delay(delayTime)
            }
        }*/

        val ogg = CustomOggContainer()
        val savingJob = scope.launch(Dispatchers.IO) {
            AsyncFileOutputFlow(File("debate scrum.ogg")).use { ogg.stream(this, it).join() }
        }

        var durationMs: Long = 0
        remixingJob = scope.launch(dispatcher) {
            ogg.addOpusHeaderPage()

            beatMap[START_INDEX]?.let {
                sendFramesToDecoder.send(it)
                ogg.addOpusAudioPackets(packets = it)
                durationMs += (jukeboxTrack.beats[0].start * 1000L).roundToLong()
            }

            var beat: JukeboxBeatAnalysis? = jukeboxTrack.beats.first()
            val lastGoodBeat = jukeboxTrack.beats.last { b -> b.neighbours.isNotEmpty() }
            val branchProbabilityRange = 18 until 50
            var branchProbability = branchProbabilityRange.first
            var branchProbabilityStep = 9
            val random = Random(System.nanoTime())

            while (isActive && beat != null) {
                beatMap[beat.which]?.let { frames ->
                    sendFramesToDecoder.send(frames)

                    if (durationMs < 300_000) {
                        durationMs += (beat!!.duration * 1_000L).toLong()

                        if (durationMs >= 300_000) {
                            ogg.addOpusAudioPackets(packets = frames, isLastPacket = true)
                            ogg.pages.close()
                        } else {
                            ogg.addOpusAudioPackets(packets = frames, isLastPacket = false)
                        }
                    }
                }

                if (beat.neighbours.isNotEmpty() && random.nextInt(100) < branchProbability || beat === lastGoodBeat) {
                    //We are permitted to branch
                    print('-')
                    beat = beat.neighbours.random().dest.next
                    branchProbability = branchProbabilityRange.first
                } else {
                    print('.')
                    beat = beat.next
                    branchProbability = (branchProbability + branchProbabilityStep).coerceIn(branchProbabilityRange)
                }

                delay(delayTime)
            }
        }

        encodingJob = scope.launch(dispatcher) {
            var framesI = Int.MAX_VALUE
            var frames: Array<ByteArray> = emptyArray() //sendFramesToEncoder.receive()
            while (isActive) {
                val playerFrame = returnFramesToDecoder.receive()

                for (i in playerFrame.indices) {
                    if (framesI >= frames.size) {
//                        returnFramesToRemixer.send(frames)
                        framesI = 0
                        frames = sendFramesToDecoder.receive()
                    }

                    decoder.decode(frames[framesI++], pcmBuffer)
                    pcmBuffer.get(playerFrame[i])
                }

//                pcmBuffer.clear()

                sendFramesToPlayer.send(playerFrame)
                delay(delayTime)
            }
        }

        playerJob = scope.launch(dispatcher) {
            val frameDuration = pcmFormat.frameDuration()

            while (isActive) {
                val frame = sendFramesToPlayer.receive()

                withContext(Dispatchers.IO) {
                    frame.forEach { array ->
                        while (isActive && line.available() < array.size) {
                            delay(frameDuration)
                        }

                        line.write(array, 0, array.size)
                    }
                }

                returnFramesToDecoder.send(frame)
                delay(delayTime)
            }
        }
    }

    fun ShortBuffer.get(byteArray: ByteArray) {
        var short: Int
        for (i in 0 until byteArray.size / 2) {
            short = get().toInt()
            byteArray[i * 2] = (short and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((short shr 8) and 0xFF).toByte()
        }
    }

    init {
        line.open(jdkFormat, (pcmFormat.maximumChunkSize() * (4000 / pcmFormat.frameDuration())).toInt()) //Have a 4s buffer
        line.start()

        repeat(CHANNEL_CAPACITY) {
            returnFramesToDecoder.offer(Array(5) { ByteArray(pcmFormat.maximumChunkSize()) })
//            returnFramesToRemixer.offer(ByteBuffer.allocateDirect(sourceFormat.maximumChunkSize()))
        }

        LAST_INSTANCE = this
    }
}