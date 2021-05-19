package dev.eternalbox.client.jvm.eternalbot.toxic

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.brella.kornea.errors.common.KorneaResult
import dev.brella.kornea.errors.common.cast
import dev.brella.kornea.errors.common.doOnSuccess
import dev.brella.kornea.errors.common.filterToInstance
import dev.brella.kornea.errors.common.getOrBreak
import dev.eternalbox.client.common.EternalboxTrack
import dev.eternalbox.client.common.arbitraryProgressBar
import dev.eternalbox.client.common.arbitrarySuspendedProgressBar
import dev.eternalbox.client.common.flatCycle
import dev.eternalbox.client.common.jukebox.EternalRemixer
import dev.eternalbox.client.common.jukebox.toJukebox
import dev.eternalbox.client.jvm.magma.loadItemAsync
import dev.eternalbox.client.jvm.magma.playTrackToAsyncFlow
import dev.eternalbox.client.jvm.magma.playTrackToAsyncFrameChannel
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileOutputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.getValue
import kotlin.collections.set
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime

class EternalboxToxicTerminalClient(val analysisPath: String = "https://cdn.eternalbox.dev/analysis/", val audioPath: String = "https://cdn.eternalbox.dev/audio/") {
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

                val client = EternalboxToxicTerminalClient()

                client.playTrack("6I9VzXrHxO9rA9A5euc8Ak").doOnSuccess { it.launch(this) }
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
    suspend fun playTrack(spotifyID: String): KorneaResult<JukeboxToxicPlayer> {
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
//            remixer.processBranches(jukeboxTrack)
        }

        return KorneaResult.success(JukeboxToxicPlayer(jukeboxTrack, beatMap))
    }

    init {
        opusManager.registerSourceManager(LocalAudioSourceManager())
        opusManager.configuration.outputFormat = StandardAudioDataFormats.DISCORD_OPUS

        opusPlayer = opusManager.createPlayer()
    }
}