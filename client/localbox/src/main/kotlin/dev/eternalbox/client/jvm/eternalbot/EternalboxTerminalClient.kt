package dev.eternalbox.client.jvm.eternalbot

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.eternalbox.common.EternalboxTrack
import dev.eternalbox.common.jukebox.EternalRemixer
import dev.eternalbox.common.arbitraryProgressBar
import dev.eternalbox.common.arbitrarySuspendedProgressBar
import dev.eternalbox.client.jvm.magma.loadItemAsync
import dev.eternalbox.client.jvm.magma.playTrackToAsyncFrameChannel
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpStatement
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import dev.brella.kornea.errors.common.*
import dev.eternalbox.common.flatCycle
import dev.eternalbox.common.jukebox.toJukebox
import dev.eternalbox.client.jvm.magma.playTrackToAsyncFlow
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime

class EternalboxTerminalClient(val analysisPath: String = "https://cdn.eternalbox.dev/analysis/", val audioPath: String = "https://cdn.eternalbox.dev/audio/") {
    companion object {
        const val START_INDEX = -1
        const val END_INDEX = -2
        const val BREAK_INDEX = -3

        @ExperimentalTime
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                println("Attach now")
                readLine()

                val client = EternalboxTerminalClient()

                client.playTrack("4KoOcR6rp5JmKLLvubzf4R").doOnSuccess { it.launch(this) }
            }
        }
    }

    val client: HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = KotlinxSerializer()
        }

        engine {
            followRedirects = true
            customizeClient {
                setUserAgent("EternalBox Client (JVM/1.1)")
            }
        }
    }
    val opusManager: AudioPlayerManager = DefaultAudioPlayerManager()
    val opusPlayer: AudioPlayer

    @ExperimentalUnsignedTypes
    @ExperimentalCoroutinesApi
    suspend fun playTrack(spotifyID: String): KorneaResult<JukeboxPlayer> {
        val audioFile = File("$spotifyID.m4a")
        if (!audioFile.exists()) {
            arbitrarySuspendedProgressBar(loadingText = "Retrieving audio...", loadedText = "Audio retrieved!") {
                client.get<HttpStatement> {
                    url("https://cdn.eternalbox.dev/audio/$spotifyID.m4a")
                }.execute { response ->
                    val channel = response.receive<ByteReadChannel>()
                    withContext(Dispatchers.IO) {
                        FileOutputStream(audioFile).use { out -> channel.copyTo(out) }
                    }
                }
            }
        }

        val analysisFile = File("$spotifyID.json")
        if (!analysisFile.exists()) {
            arbitrarySuspendedProgressBar(loadingText = "Retrieving analysis...", loadedText = "Analysis retrieved!") {
                val analysis = client.get<JsonObject> {
                    url("https://cdn.eternalbox.dev/analysis/$spotifyID.json")
                }.getValue("analysis").jsonObject

                withContext(Dispatchers.IO) { analysisFile.writeText(Json.encodeToString(analysis)) }
            }
        }

        val jukeboxTrack = withContext(Dispatchers.IO) { Json.decodeFromString<EternalboxTrack>(analysisFile.readText()).toJukebox() }
        val audioTrack = opusManager.loadItemAsync(audioFile.absolutePath)
                .filterToInstance<AudioTrack>()
                .getOrBreak { return it.cast() }

        val beatMap: MutableMap<Int, Array<ByteArray>> = HashMap()
//        val beatMap = DataMap.MemoryEfficient<Int>()

        arbitrarySuspendedProgressBar(loadingText = "Generating beatmap...", loadedText = "Beatmap generated in {0}!") {
            var beatIndex: Int = START_INDEX
            var nextBeatIndex: Int = 0
            var beatRange = 0 until (jukeboxTrack.beats[0].start * 1000L).roundToLong()
            val frames: MutableList<ByteArray> = ArrayList()
//            var beatLength: Int = 0

            opusPlayer.playTrackToAsyncFlow(audioTrack)
                .collect { frame ->
                    while (frame.timecode !in beatRange) {
//                            beatMap.putIntoBuffer(beatIndex, frames, beatLength)
                        beatMap[beatIndex] = Array(frames.size, frames::get)
                        frames.clear()
//                            beatLength = 0

                        if (nextBeatIndex >= jukeboxTrack.beats.size) {
                            val beat = jukeboxTrack.beats[beatIndex]

                            beatIndex = END_INDEX
                            nextBeatIndex = BREAK_INDEX

                            beatRange = (beat.end * 1000L).roundToLong() until Long.MAX_VALUE
                        } else {
                            beatIndex = nextBeatIndex
                            val beat = jukeboxTrack.beats[nextBeatIndex++]
                            beatRange = (beat.start * 1000L).roundToLong() until (beat.end * 1000).roundToLong()
                        }
                    }

                    frames.add(frame.data)
//                        beatLength += frame.dataLength
                }

//            beatMap.putIntoBuffer(beatIndex, frames, beatLength)
            beatMap[beatIndex] = Array(frames.size, frames::get)
        }

        arbitraryProgressBar(loadingText = "Remixing track...", loadedText = "Remix complete!") {
            val remixer = EternalRemixer()
            remixer.preprocessTrack(jukeboxTrack)
            remixer.processBranches(jukeboxTrack)
        }

        return KorneaResult.success(JukeboxPlayer(jukeboxTrack, beatMap))
    }

    init {
        opusManager.registerSourceManager(LocalAudioSourceManager())
        opusManager.configuration.outputFormat = StandardAudioDataFormats.DISCORD_OPUS

        opusPlayer = opusManager.createPlayer()
    }
}