package dev.eternalbox.eternaljukebox.providers.analysis

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.providers.analysis.AnalysisProvider.Companion.parseAnalysisData
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class SpotifyAnalysisProvider(val jukebox: EternalJukebox) : AnalysisProvider {
    val spotifyClientID: String = requireNotNull(jukebox["spotify_client_id"]).asText()
    val spotifyClientSecret: String = requireNotNull(jukebox["spotify_client_secret"]).asText()

    var spotifyToken: String? = null
    val spotifyTokenMutex = Mutex()
    var spotifyTokenJob = spotifyTokenJob()

    override suspend fun supportsAnalysis(service: EnumAnalysisService): Boolean =
        service == EnumAnalysisService.SPOTIFY

    override suspend fun getAnalysisFor(service: EnumAnalysisService, id: String): DataResponse? {
        if (service != EnumAnalysisService.SPOTIFY) return null

        return (jukebox.analysisDataStore.getAnalysis(service, id) as? JukeboxResult.Success)?.result
    }

    override suspend fun retrieveAnalysisFor(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse> {
        if (service != EnumAnalysisService.SPOTIFY) return JukeboxResult.KnownFailure(
            WebApiResponseCodes.INVALID_ANALYSIS_SERVICE,
            WebApiResponseMessages.INVALID_ANALYSIS_SERVICE
        )

        return spotifyTokenMutex.withLock {
            val (data, error) = Fuel.get("https://api.spotify.com/v1/audio-analysis/$id")
                .authentication()
                .bearer(requireNotNull(spotifyToken))
                .awaitByteArrayResult()

            when {
                data != null -> {
                    parseAnalysisData(data)
                        .flatMapAwait { parsed ->
                            jukebox.analysisDataStore.storeAnalysis(service, id, withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsBytes(parsed) })
                        }
                }
                error != null -> {
                    if (error.response.statusCode == 401) {
                        invalidateToken()
                    }

                    JukeboxResult.KnownFailure(
                        error.response.statusCode,
                        error.response.responseMessage,
                        JSON_MAPPER.readTree(error.response.data)
                    )
                }
                else -> JukeboxResult.UnknownFailure()
            }
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