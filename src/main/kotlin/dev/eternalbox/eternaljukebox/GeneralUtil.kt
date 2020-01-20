package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArray
import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.EnumAnalysisService
import dev.eternalbox.eternaljukebox.data.EnumAudioService
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.WriteStream
import io.vertx.kotlin.core.streams.writeAwait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.receiveOrNull
import java.io.InputStream
import kotlin.contracts.ExperimentalContracts
import kotlin.coroutines.CoroutineContext

typealias FuelResult<V, E> = com.github.kittinunf.result.Result<V, E>

public inline fun <T, reified R> Array<out T>.mapArray(transform: (T) -> R): Array<R> =
    Array(size) { transform(this[it]) }


@ExperimentalCoroutinesApi
suspend fun <T : Any> ReceiveChannel<T>.copyTo(sendChannel: SendChannel<T>) {
    while (!this.isClosedForReceive) {
        val buffer = this.receiveOrNull() ?: break
        if (sendChannel.isClosedForSend)
            break
        sendChannel.send(buffer)
    }
}

@ExperimentalCoroutinesApi
suspend fun <T : Any> ReceiveChannel<T>.copyTo(writeStream: WriteStream<T>) {
    while (!this.isClosedForReceive) {
        writeStream.writeAwait(this.receiveOrNull() ?: break)
    }

    writeStream.end()
}

@Suppress("BlockingMethodInNonBlockingContext")
@ExperimentalCoroutinesApi
suspend fun InputStream.asVertxChannel(
    coroutineScope: CoroutineScope = GlobalScope,
    context: CoroutineContext = Dispatchers.IO,
    capacity: Int = 1
): ReceiveChannel<Buffer> =
    coroutineScope.produce(context, capacity = capacity) {
        use { stream ->
            invokeOnClose { stream.close() }

            val buffer = ByteArray(8192)
            var read: Int

            while (true) {
                read = stream.read(buffer)
                if (read == -1) {
                    break
                }

                send(Buffer.buffer(buffer.copyOfRange(0, read)))
            }
        }
    }

private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
fun bytesToHex(bytes: ByteArray): String {
    val hexChars = CharArray(bytes.size * 2)
    for (j in bytes.indices) {
        val v: Int = bytes[j].toInt() and 0xFF
        hexChars[j * 2] = HEX_ARRAY[v shr 4]
        hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
    }
    return String(hexChars)
}

@ExperimentalContracts
@ExperimentalCoroutinesApi
suspend inline fun <reified T> DataResponse.retrieve(jukebox: EternalJukebox): T =
    if (this is DataResponse.ExternalUrl && this.localData != null)
        localData._retrieve(jukebox.host)
    else
        _retrieve(jukebox.host)

suspend inline fun <reified T> DataResponse.retrieve(host: String): T =
    if (this is DataResponse.ExternalUrl && this.localData != null)
        localData._retrieve(host)
    else
        _retrieve(host)

suspend inline fun <reified T> DataResponse._retrieve(host: String): T =
    when (this) {
        is DataResponse.Data -> JSON_MAPPER.readValue(this.data)
        is DataResponse.DataSource -> JSON_MAPPER.readValue(this.source().use(InputStream::readBytes))
        is DataResponse.ExternalUrl -> Fuel.get(
            if (this.url.startsWith("/"))
                "${host}${this.url}"
            else
                this.url
        ).awaitByteArray().let { data -> JSON_MAPPER.readValue<T>(data) }
    }

@ExperimentalCoroutinesApi
@ExperimentalContracts
suspend fun EternalJukebox.getOrRetrieveAnalysis(
    service: EnumAnalysisService,
    songID: String
): JukeboxResult<DataResponse> {
    if (!analysisDataStore.hasAnalysisStored(service, songID)) {
        if (analysisDataStore.canStoreAnalysis(service, songID)) {
            val errors: MutableList<JukeboxResult.Failure<DataResponse>> = ArrayList()
            for (provider in analysisProviders) {
                if (provider.supportsAnalysis(service)) {
                    val result = provider.retrieveAnalysisFor(service, songID)
                    if (result is JukeboxResult.Success) {
                        return result
                    } else if (result is JukeboxResult.Failure) {
                        errors.add(result)
                    }
                }
            }

            return JukeboxResult.ListFailure(errors)
        } else {
            return JukeboxResult.UnknownFailure()
        }
    } else {
        return analysisDataStore.getAnalysis(service, songID)
    }
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
suspend fun EternalJukebox.getOrRetrieveAudio(
    audioService: EnumAudioService,
    analysisService: EnumAnalysisService,
    songID: String
): JukeboxResult<DataResponse> {
    if (!audioDataStore.hasAudioStored(audioService, analysisService, songID)) {
        if (audioDataStore.canStoreAudio(audioService, analysisService, songID)) {
            val errors: MutableList<JukeboxResult.Failure<DataResponse>> = ArrayList()
            for (provider in audioProviders) {
                if (provider.supportsAudio(audioService, analysisService)) {
                    val result = provider.retrieveAudioFor(audioService, analysisService, songID)
                    if (result is JukeboxResult.Success) {
                        return result
                    } else if (result is JukeboxResult.Failure) {
                        errors.add(result)
                    }
                }
            }

            return JukeboxResult.ListFailure(errors)
        } else {
            return JukeboxResult.UnknownFailure()
        }
    } else {
        return audioDataStore.getAudio(audioService, analysisService, songID)
    }
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
suspend fun EternalJukebox.getOrRetrieveTrackInfo(
    service: EnumAnalysisService,
    songID: String
): JukeboxResult<DataResponse> {
    if (!infoDataStore.hasTrackInfoStored(service, songID)) {
        if (infoDataStore.canStoreTrackInfo(service, songID)) {
            val errors: MutableList<JukeboxResult.Failure<DataResponse>> = ArrayList()
            for (provider in infoProviders) {
                if (provider.supportsTrackInfo(service)) {
                    val result = provider.retrieveTrackInfoFor(service, songID)
                    if (result is JukeboxResult.Success) {
                        return result
                    } else if (result is JukeboxResult.Failure) {
                        errors.add(result)
                    }
                }
            }

            return JukeboxResult.ListFailure(errors)
        } else {
            return JukeboxResult.UnknownFailure()
        }
    } else {
        return infoDataStore.getTrackInfo(service, songID)
    }
}