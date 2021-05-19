package dev.eternalbox.client.jvm.magma

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackState
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import dev.eternalbox.client.common.ExchangeChannel
import dev.eternalbox.client.common.exchange
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import kotlin.math.pow

/**
 * Lowball this
 */
const val AVERAGE_SONG_DURATION_MS = 120_000

suspend fun AudioPlayer.playTrackAsync(track: AudioTrack): Array<AudioFrame> {
    playTrack(track)

    return withContext(Dispatchers.IO) {
        var buffer: MutableList<AudioFrame>? = null
        var emptyCount = 0

        while (isActive && track.state == AudioTrackState.LOADING) delay(50)
        while (isActive && emptyCount < 6 && (track.state == AudioTrackState.PLAYING || buffer == null)) {
            val frame = provide()
            when {
                frame == null -> delay(2.0.pow(emptyCount++).toInt() * 50L)
                buffer == null -> {
                    if (track.duration != Long.MAX_VALUE) {
                        val bufferLimit = (track.duration / frame.format.frameDuration()).toInt()

//                        if (buffer is ArrayList) buffer.ensureCapacity(bufferLimit)
                        buffer = ArrayList(bufferLimit)

                        if (bufferLimit <= 0) return@withContext emptyArray()
                    } else {
                        buffer = ArrayList((AVERAGE_SONG_DURATION_MS / frame.format.frameDuration()).toInt())
                    }

                    buffer.add(frame)
                }
                else -> {
                    buffer.add(frame)
                }
            }

            yield()
        }

        if (buffer != null) {
            while (isActive) {
                val frame = provide()
                if (frame == null) break
                else buffer.add(frame)

                yield()
            }
        } else {
            return@withContext emptyArray()
        }

        val frames = buffer.toTypedArray()
        buffer.clear()

        return@withContext frames
    }
}

@FlowPreview
@ExperimentalCoroutinesApi
suspend fun AudioPlayer.playTrackToAsyncChannel(track: AudioTrack, scope: CoroutineScope, capacity: Int = Channel.Factory.RENDEZVOUS): ReceiveChannel<AudioFrame> {
//    playTrack(track)
//
//    return scope.produce(Dispatchers.IO, capacity) {
//        while (isActive && track.state == AudioTrackState.LOADING) delay(50)
//        while (isActive && track.state == AudioTrackState.PLAYING) {
//            val frame = provide()
//            if (frame == null) delay(50)
//            else send(frame)
//
//            yield()
//        }
//
//        while (isActive) {
//            val frame = provide()
//            if (frame == null) break
//            else send(frame)
//
//            yield()
//        }
//
//        close()
//    }

    return playTrackToAsyncFlow(track)
            .buffer(capacity)
            .flowOn(Dispatchers.IO)
            .produceIn(scope)
}

@ExperimentalCoroutinesApi
fun AudioPlayer.playTrackToAsyncFlow(track: AudioTrack) = channelFlow {
    playTrack(track)

    while (isActive && (track.state == AudioTrackState.INACTIVE || track.state == AudioTrackState.LOADING)) delay (50)
    while (isActive && track.state == AudioTrackState.PLAYING) {
        val frame = provide()
        if (frame == null) delay(50)
        else send(frame)

        yield()
    }

    while (isActive) {
        val frame = provide()
        if (frame == null) break
        else send(frame)

        yield()
    }
}

@ExperimentalCoroutinesApi
fun AudioPlayer.playTrackToAsyncFrameChannel(track: AudioTrack, scope: CoroutineScope, capacity: Int = Channel.Factory.RENDEZVOUS): ExchangeChannel<MutableAudioFrame> {
    playTrack(track)

    return scope.exchange(Dispatchers.IO, capacity) {
        while (isActive && track.state == AudioTrackState.LOADING) delay(50)
        while (isActive && track.state == AudioTrackState.PLAYING) {
            val frame = provide()
            if (frame == null) delay(50)
            else {
                val audioFormat = frame.format
                val frameAsMutable = ExchangedMutableAudioFrame(audioFormat)
                frameAsMutable.store(frame.data, 0, frame.dataLength)
                send(frameAsMutable)

                launch {
                    var i = 1
                    val range = (0 until capacity.coerceIn(0, 64))
                    while (isActive && i++ in range) {
                        val mutableFrame = ExchangedMutableAudioFrame(audioFormat)

                        inputAdvanced.send(mutableFrame)
                    }
                }

                break
            }
        }

        var frame: MutableAudioFrame? = null
        while (isActive && track.state == AudioTrackState.PLAYING) {
            if (frame == null) frame = receiveCatching().getOrNull() ?: break
            if (provide(frame)) {
                send(frame)
                frame = null
            } else delay(50)

            yield()
        }

        println("[Track State: ${track.state}]")

        while (isActive) {
            if (frame == null) frame = receiveCatching().getOrNull() ?: break
            if (provide(frame)) {
                send(frame)
                frame = null
            } else {
                println("[No frames left!]")
                break
            }

            yield()
        }

        println("[Closing]")
//        close()
    }
}