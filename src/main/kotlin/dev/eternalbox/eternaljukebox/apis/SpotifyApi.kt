package dev.eternalbox.eternaljukebox.apis

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.asResult
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import dev.eternalbox.eternaljukebox.data.isGatewayTimeout
import dev.eternalbox.eternaljukebox.data.isUnauthenticatedFailure
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrack
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrackAnalysis
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.pow
import kotlin.random.Random

@ExperimentalCoroutinesApi
@ExperimentalContracts
class SpotifyApi(jukebox: EternalJukebox) {
    val spotifyClientID: String = requireNotNull(jukebox["spotify_client_id"]).asText()
    val spotifyClientSecret: String = requireNotNull(jukebox["spotify_client_secret"]).asText()

    lateinit var spotifyToken: String
    val spotifyTokenMutex = Mutex()
    var spotifyTokenJob = spotifyTokenJob()

    suspend fun getTrack(trackID: String): JukeboxResult<SpotifyTrack> =
        exponentialBackoff {
            withSpotifyToken { token ->
                Fuel.get("https://api.spotify.com/v1/tracks/$trackID")
                    .authentication()
                    .bearer(token)
                    .awaitByteArrayResult()
                    .asResult<SpotifyTrack>()
            }
        }

    suspend fun getTrackAnalysis(trackID: String): JukeboxResult<SpotifyTrackAnalysis> =
        exponentialBackoff {
            withSpotifyToken { token ->
                Fuel.get("https://api.spotify.com/v1/audio-analysis/$trackID")
                    .authentication()
                    .bearer(token)
                    .awaitByteArrayResult()
                    .asResult<SpotifyTrackAnalysis>()
                    .also { result -> if (result.isUnauthenticatedFailure()) invalidateToken() }
            }
        }

    private fun invalidateToken() {
        spotifyTokenJob.cancel()
        spotifyTokenJob = spotifyTokenJob()
    }

    private fun spotifyTokenJob() = GlobalScope.launch {
        while (isActive) {
            spotifyTokenMutex.lock()
            val tokenResult = retrieveToken()
            if (tokenResult is JukeboxResult.Success) {
                val tokenInfo = tokenResult.result
                spotifyToken = tokenInfo["access_token"].asText()
                spotifyTokenMutex.unlock()
                val expiresIn = (tokenInfo["expires_in"].asLong() * 0.8).toLong()
                println("Sleeping for $expiresIn")
                delay(expiresIn * 1_000)
            } else {
                println("Token failed: $tokenResult")
                spotifyTokenMutex.unlock()
                break
            }
        }
    }

    private suspend fun retrieveToken(): JukeboxResult<JsonNode> =
        exponentialBackoff<JsonNode> {
            val (data, error) = Fuel.post("https://accounts.spotify.com/api/token")
                .authentication()
                .basic(spotifyClientID, spotifyClientSecret)
                .body("grant_type=client_credentials")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .awaitByteArrayResult()

            when {
                data != null -> withContext(Dispatchers.IO) { JukeboxResult.Success(JSON_MAPPER.readTree(data)) }
                error != null -> JukeboxResult.KnownFailure(error.response.statusCode, error.response.responseMessage)
                else -> JukeboxResult.UnknownFailure()
            }
        }

    private suspend fun <R> exponentialBackoff(
        maximumBackoff: Int = 4,
        block: suspend () -> JukeboxResult<R>
    ): JukeboxResult<R> {
        var response: JukeboxResult<R>? = null
        var invalidated = false
        for (i in 0 until maximumBackoff) {
            response = block()
            if (response.isUnauthenticatedFailure() && !invalidated) {
                invalidateToken()
                invalidated = true

                delay(2.0.pow(i.toDouble()).toLong() * 1000 + Random.nextLong(500))
                continue
            } else if (response.isGatewayTimeout()) {
                delay(2.0.pow(i.toDouble()).toLong() * 1000 + Random.nextLong(500))
                continue
            }

            return response
        }

        return response!!
    }
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
private suspend inline fun <R> SpotifyApi.withSpotifyToken(block: (String) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    spotifyTokenMutex.lock()
    try {
        return block(spotifyToken)
    } finally {
        spotifyTokenMutex.unlock()
    }
}