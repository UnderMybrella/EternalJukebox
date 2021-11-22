package dev.eternalbox.audio.ytdlbox

import dev.brella.ytdlbox.CompletionRequest
import dev.brella.ytdlbox.YtdlBoxCompletionActionFeatureSet
import dev.brella.ytdlbox.client.remoteBox
import dev.eternalbox.audio.AudioApi
import dev.eternalbox.audio.EnumAudioType
import dev.eternalbox.audio.ytmsearch.YtmSearchApi
import dev.eternalbox.common.jukebox.EternalboxTrackDetails
import dev.eternalbox.common.jvm.hash
import dev.eternalbox.common.utils.getString
import dev.eternalbox.storage.base.EternalData
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

class YtdlboxAudioApi(
    val url: String,
    val auth: String
) : AudioApi, CoroutineScope {
    constructor(config: JsonObject) : this(config.getString("url"), config.getString("auth"))

    override val service: String = "ytdlbox"
    override val coroutineContext: CoroutineContext = SupervisorJob()

    val json = Json {
        ignoreUnknownKeys = true
    }

    val client = HttpClient {
        install(ContentEncoding) {
            gzip()
            deflate()
            identity()
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 30_000L
        }

        install(WebSockets)
    }

    val youtubeMusicApi = YtmSearchApi(client)
    val remoteBox = async { client.remoteBox(url, auth, Pair(ContentType.Application.Json, json)) }
    val remoteBoxInfo = async { remoteBox.await().getServerInfo() }

    override suspend fun getAudioUrl(track: EternalboxTrackDetails, type: EnumAudioType): String? =
        youtubeMusicApi.fromEternalboxTrack(track)?.let { result -> "https://music.youtube.com/watch?v=${result.videoID}" }

    override suspend fun getAudio(url: String, type: EnumAudioType, track: EternalboxTrackDetails): EternalData? {
        val remoteBoxInfo = remoteBoxInfo.await()
        val remoteBoxRClone = remoteBoxInfo.completionActions.firstOrNull { it is YtdlBoxCompletionActionFeatureSet.RClone } as? YtdlBoxCompletionActionFeatureSet.RClone
        if (remoteBoxRClone?.resultingBaseUrl != null) {
            val path = "/audio/by_hash/${url.hash("SHA-256")}.${type.extension}"

            val (downloadSuccess, downloadFailure) = remoteBox.await()
                .downloadWithoutData(url, listOf("-x", "--audio-format", type.youtubeDLName), listOf(CompletionRequest.UploadWithRClone(path)))

            return if (downloadSuccess != null)
                EternalData.Uploaded("${remoteBoxRClone.resultingBaseUrl}${path}")
            else
                null
        } else {
            val (success, failure) = remoteBox.await()
                .downloadWithData(url, listOf("-x", "--audio-format", type.youtubeDLName), emptyList())

            return if (success != null)
                EternalData.Raw(success.output, "${url.hash("SHA-256")}.${type.extension}", success.mimeType ?: type.mimeType)
            else
                null
        }
    }
}