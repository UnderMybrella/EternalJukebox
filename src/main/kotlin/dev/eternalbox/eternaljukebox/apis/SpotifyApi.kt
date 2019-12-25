package dev.eternalbox.eternaljukebox.apis

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.FuelResult
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrack
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrackAnalysis
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class SpotifyApi(jukebox: EternalJukebox) {
    val spotifyClientID: String = requireNotNull(jukebox["spotify_client_id"]).asText()
    val spotifyClientSecret: String = requireNotNull(jukebox["spotify_client_secret"]).asText()

    var spotifyToken: String? = null
    val spotifyTokenMutex = Mutex()
    var spotifyTokenJob = spotifyTokenJob()

    private suspend inline fun <reified T> FuelResult<ByteArray, FuelError>.asResult(): JukeboxResult<T> =
        getResponseFromResult(this)

    private suspend inline fun <reified T> getResponseFromResult(result: FuelResult<ByteArray, FuelError>): JukeboxResult<T> =
        getResponseFromResult(result.component1(), result.component2())

    private suspend inline fun <reified T> getResponseFromResult(
        data: ByteArray?,
        error: FuelError?
    ): JukeboxResult<T> =
        when {
            data != null -> JukeboxResult.Success(JSON_MAPPER.readValue(data))
            error != null -> {
                if (error.response.statusCode == 401) {
                    invalidateToken()
                }

                JukeboxResult.KnownFailure(
                    error.response.statusCode,
                    error.response.responseMessage,
                    withContext(Dispatchers.IO) { JSON_MAPPER.readTree(error.response.data) }
                )
            }
            else -> JukeboxResult.UnknownFailure()
        }

    suspend fun getTrack(trackID: String): JukeboxResult<SpotifyTrack> {
        return spotifyTokenMutex.withLock {
            Fuel.get("https://api.spotify.com/v1/tracks/$trackID")
                .authentication()
                .bearer(requireNotNull(spotifyToken))
                .awaitByteArrayResult()
                .asResult()
        }
    }

    suspend fun getTrackAnalysis(trackID: String): JukeboxResult<SpotifyTrackAnalysis> {
        return spotifyTokenMutex.withLock {
            Fuel.get("https://api.spotify.com/v1/audio-analysis/$trackID")
                .authentication()
                .bearer(requireNotNull(spotifyToken))
                .awaitByteArrayResult()
                .asResult()
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

    private suspend fun retrieveToken(): JukeboxResult<JsonNode> {
        val (data, error) = Fuel.post("https://accounts.spotify.com/api/token")
            .authentication()
            .basic(spotifyClientID, spotifyClientSecret)
            .body("grant_type=client_credentials")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .awaitByteArrayResult()

        return when {
            data != null -> withContext(Dispatchers.IO) { JukeboxResult.Success(JSON_MAPPER.readTree(data)) }
            error != null -> JukeboxResult.KnownFailure(error.response.statusCode, error.response.responseMessage)
            else -> JukeboxResult.UnknownFailure()
        }
    }
}