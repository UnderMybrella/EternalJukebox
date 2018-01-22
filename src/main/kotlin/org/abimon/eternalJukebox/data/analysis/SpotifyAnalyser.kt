package org.abimon.eternalJukebox.data.analysis

import com.github.kittinunf.fuel.Fuel
import io.vertx.core.json.JsonObject
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.*
import org.abimon.visi.io.ByteArrayDataSource
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.withLock

object SpotifyAnalyser : IAnalyser {
    private val REAUTH_TIMER = Timer()
    var token: String = ""
    val tokenLock = ReentrantLock()

    override fun search(query: String, clientInfo: ClientInfo?): Array<JukeboxInfo> {
        var error: SpotifyError? = null
        val array: ArrayList<JukeboxInfo> = arrayListOf()
        val success = exponentiallyBackoff(16000, 8) {
            log("[${clientInfo?.userUID}] Attempting to search Spotify for \"$query\"")
            val (_, response, _) = tokenLock.withLock { Fuel.get("https://api.spotify.com/v1/search", listOf("q" to query, "type" to "track")).bearer(token).responseString() }
            val mapResponse = EternalJukebox.jsonMapper.readValue(response.data, Map::class.java)

            when (response.statusCode) {
                200 -> {
                    ((mapResponse["tracks"] as Map<*, *>)["items"] as List<*>).filter { it is Map<*, *> && it.containsKey("id") }.map {
                        val track = it as Map<*, *>
                        array.add(JukeboxInfo("SPOTIFY", track["id"] as String, track["name"] as String, track["name"] as String, ((track["artists"] as List<*>).first() as Map<*, *>)["name"] as String, "https://open.spotify.com/track/${track["id"] as String}", track["duration_ms"] as Int))
                    }
                    return@exponentiallyBackoff false
                }
                400 -> {
                    if (((mapResponse["error"] as Map<*, *>)["message"] as String) == "Only valid bearer authentication supported") {
                        log("[${clientInfo?.userUID}] Got back response code 400  with error \"Only valid bearer authentication supported\"; reloading token, backing off, and trying again")
                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning INVALID_SEARCH_DATA")
                        error = SpotifyError.INVALID_SEARCH_DATA
                        return@exponentiallyBackoff false
                    }
                }
                401 -> {
                    log("[${clientInfo?.userUID}] Got back response code 401  with data ${String(response.data)}; reloading token, backing off, and trying again")
                    reload()
                    return@exponentiallyBackoff true
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && error == null

        if (success)
            log("[${clientInfo?.userUID}] Successfully searched for \"$query\"")
        else
            log("[${clientInfo?.userUID}] Failed to search for \"query\". Error: $error")

        return array.toTypedArray()
    }

    override fun analyse(id: String, clientInfo: ClientInfo?): JukeboxTrack? {
        var error: SpotifyError? = null
        var track: JukeboxTrack? = null
        val info = getInfo(id, clientInfo)

        if (info == null) {
            log("[${clientInfo?.userUID}] Failed to analyse $id on Spotify; track info was null")
            return null
        }

        val success = exponentiallyBackoff(16000, 8) {
            log("[${clientInfo?.userUID}] Attempting to analyse $id on Spotify")
            val (_, response, _) = tokenLock.withLock { Fuel.get("https://api.spotify.com/v1/audio-analysis/$id").bearer(token).responseString() }
            val mapResponse = EternalJukebox.jsonMapper.tryReadValue(response.data, Map::class)

            when (response.statusCode) {
                200 -> {
                    if(mapResponse == null) {
                        val name = "SPOTIFY-RESPONSE-200-${UUID.randomUUID()}.txt"
                        if(EternalJukebox.storage.shouldStore(EnumStorageType.LOG) && EternalJukebox.storage.store(name, EnumStorageType.LOG, ByteArrayDataSource(response.data), clientInfo)) {
                            log("[${clientInfo?.userUID}] Got back response code 200; invalid response body however; saved as $name")
                            return@exponentiallyBackoff true
                        } else {
                            log("[${clientInfo?.userUID}] Got back response code 200; invalid response body however; did not save due to an error or log saving being disabled")
                            return@exponentiallyBackoff true
                        }
                    }

                    val obj = JsonObject(mapResponse.mapKeys { (key) -> "$key" })
                    track = JukeboxTrack(
                            info,
                            JukeboxAnalysis(
                                    EternalJukebox.jsonMapper.readValue(obj.getJsonArray("sections").toString(), Array<SpotifyAudioSection>::class.java),
                                    EternalJukebox.jsonMapper.readValue(obj.getJsonArray("bars").toString(), Array<SpotifyAudioBar>::class.java),
                                    EternalJukebox.jsonMapper.readValue(obj.getJsonArray("beats").toString(), Array<SpotifyAudioBeat>::class.java),
                                    EternalJukebox.jsonMapper.readValue(obj.getJsonArray("tatums").toString(), Array<SpotifyAudioTatum>::class.java),
                                    EternalJukebox.jsonMapper.readValue(obj.getJsonArray("segments").toString(), Array<SpotifyAudioSegment>::class.java)
                            ), JukeboxSummary((mapResponse["track"] as Map<*, *>)["duration"] as Double)
                    )

                    return@exponentiallyBackoff false
                }
                400 -> {
                    if(mapResponse == null) {
                        val name = "SPOTIFY-RESPONSE-400-${UUID.randomUUID()}.txt"
                        if(EternalJukebox.storage.shouldStore(EnumStorageType.LOG) && EternalJukebox.storage.store(name, EnumStorageType.LOG, ByteArrayDataSource(response.data), clientInfo)) {
                            log("[${clientInfo?.userUID}] Got back response code 400; invalid response body however; saved as $name")
                            return@exponentiallyBackoff true
                        } else {
                            log("[${clientInfo?.userUID}] Got back response code 400; invalid response body however; did not save due to an error or log saving being disabled")
                            return@exponentiallyBackoff true
                        }
                    }

                    if (((mapResponse["error"] as Map<*, *>)["message"] as String) == "Only valid bearer authentication supported") {
                        log("[${clientInfo?.userUID}] Got back response code 400  with error \"Only valid bearer authentication supported\"; reloading token, backing off, and trying again")
                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning INVALID_SEARCH_DATA")
                        error = SpotifyError.INVALID_SEARCH_DATA
                        return@exponentiallyBackoff false
                    }
                }
                401 -> {
                    log("[${clientInfo?.userUID}] Got back response code 401  with data ${String(response.data)}; reloading token, backing off, and trying again")
                    reload()
                    return@exponentiallyBackoff true
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && error == null

        if (success)
            log("[${clientInfo?.userUID}] Successfully analysed $id from Spotify")
        else
            log("[${clientInfo?.userUID}] Failed to analyse $id. Error: $error")

        return track
    }

    override fun getInfo(id: String, clientInfo: ClientInfo?): JukeboxInfo? {
        var error: SpotifyError? = null
        var track: JukeboxInfo? = null

        val success = exponentiallyBackoff(16000, 8) {
            log("[${clientInfo?.userUID}] Attempting to get info for $id on Spotify")
            val (_, response, _) = tokenLock.withLock { Fuel.get("https://api.spotify.com/v1/tracks/$id").bearer(token).responseString() }
            val mapResponse = EternalJukebox.jsonMapper.readValue(response.data, Map::class.java)

            when (response.statusCode) {
                200 -> {
                    track = JukeboxInfo("SPOTIFY", mapResponse["id"] as String, mapResponse["name"] as String, mapResponse["name"] as String, ((mapResponse["artists"] as List<*>).first() as Map<*, *>)["name"] as String, "https://open.spotify.com/mapResponse/${mapResponse["id"] as String}", mapResponse["duration_ms"] as Int)
                    return@exponentiallyBackoff false
                }
                400 -> {
                    if (((mapResponse["error"] as Map<*, *>)["message"] as String) == "Only valid bearer authentication supported") {
                        log("[${clientInfo?.userUID}] Got back response code 400  with error \"Only valid bearer authentication supported\"; reloading token, backing off, and trying again")
                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning INVALID_SEARCH_DATA")
                        error = SpotifyError.INVALID_SEARCH_DATA
                        return@exponentiallyBackoff false
                    }
                }
                401 -> {
                    log("[${clientInfo?.userUID}] Got back response code 401  with data ${String(response.data)}; reloading token, backing off, and trying again")
                    reload()
                    return@exponentiallyBackoff true
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && error == null

        if (success)
            log("[${clientInfo?.userUID}] Successfully obtained info for $id off of Spotify")
        else
            log("[${clientInfo?.userUID}] Failed to obtain info for $id. Error: $error")

        return track
    }

    fun reload(): SpotifyError? {
        tokenLock.withLock {
            var error: SpotifyError? = null
            val success = exponentiallyBackoff(64000, 8) {
                log("Attempting to reload Spotify Token")
                val (_, response, _) =
                        Fuel.post("https://accounts.spotify.com/api/token").body("grant_type=client_credentials").authenticate(EternalJukebox.config.spotifyClient ?: run {
                            error = SpotifyError.NO_AUTH_DETAILS
                            return@exponentiallyBackoff false
                        }, EternalJukebox.config.spotifySecret ?: run {
                            error = SpotifyError.NO_AUTH_DETAILS
                            return@exponentiallyBackoff false
                        }).responseString()

                when (response.statusCode) {
                    200 -> {
                        token = JsonObject(String(response.data, Charsets.UTF_8)).getString("access_token")
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        log("Got back response code 400 with data ${String(response.data)}; returning INVALID_AUTH_DETAILS")
                        error = SpotifyError.INVALID_AUTH_DETAILS
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        log("Got back response code 401  with data ${String(response.data)}; returning INVALID_AUTH_DETAILS")
                        error = SpotifyError.INVALID_AUTH_DETAILS
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        log("Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                        return@exponentiallyBackoff true
                    }
                }
            } && error == null

            if (!success)
                log("Failed to reload the Spotify token. Error: $error")
            else
                log("Successfully reloaded the Spotify token")
            return error
        }
    }

    init {
        REAUTH_TIMER.scheduleAtFixedRate(0, 3000 * 1000) { reload() }
    }
}