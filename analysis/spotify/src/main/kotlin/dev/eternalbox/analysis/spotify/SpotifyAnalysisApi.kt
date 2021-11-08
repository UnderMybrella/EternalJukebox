package dev.eternalbox.analysis.spotify

import dev.eternalbox.analysis.AnalysisApi
import dev.eternalbox.common.EternalboxTrack
import dev.eternalbox.common.jukebox.EternalboxTrackDetails
import dev.eternalbox.common.utils.TokenStore
import dev.eternalbox.httpclient.authorise
import dev.eternalbox.httpclient.formDataContent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class SpotifyAnalysisApi(clientID: String, clientSecret: String) : AnalysisApi, CoroutineScope {
    override val service: String = "SPOTIFY"
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
    }

    private val clientCredentials: String = Base64
        .getEncoder()
        .encodeToString("$clientID:$clientSecret".encodeToByteArray())

    @OptIn(ExperimentalTime::class)
    val authToken = TokenStore(this) {
        val response = client.post<HttpResponse>("https://accounts.spotify.com/api/token") {
            header("Authorization", "Basic $clientCredentials")

            body = formDataContent {
                append("grant_type", "client_credentials")
            }
        }

        if (response.status.isSuccess()) {
            val token = response.receive<SpotifyClientCredentialsToken>()

            TokenStore.TokenResult(token.accessToken, Duration.seconds(token.expiresIn))
        } else {
            null
        }
    }

    override suspend fun getAnalysis(trackID: String): EternalboxTrack? =
        authToken.authorise { token ->
            client.get("https://api.spotify.com/v1/audio-analysis/$trackID") {
                header("Authorization", "Bearer $token")
            }
        }

    override suspend fun getTrackDetails(trackID: String): EternalboxTrackDetails? {
        val track: SpotifyTrack = authToken.authorise { token ->
            client.get("https://api.spotify.com/v1/tracks/$trackID") {
                header("Authorization", "Bearer $token")
            }
        } ?: return null

        return EternalboxTrackDetails(
            service = service,
            name = track.name,
            albumName = track.album.name,
            imageUrl = track.album.images.firstOrNull()?.url,
            artists = track.artists.map(SpotifyArtist::name),
            durationMs = track.durationMs
        )
    }
}