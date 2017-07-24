package org.abimon.eternalJukebox.data.analysis

import com.github.kittinunf.fuel.Fuel
import io.vertx.core.json.JsonObject
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.bearer
import org.abimon.eternalJukebox.exponentiallyBackoff
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.eternalJukebox.objects.JukeboxTrack
import org.abimon.eternalJukebox.objects.SpotifyError
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.concurrent.withLock

object SpotifyAnalyser : IAnalyser {
    private val REAUTH_TIMER = Timer()
    var token: String = ""
    val tokenLock = ReentrantLock()

    override fun search(query: String): Array<JukeboxInfo> {
        var error: SpotifyError? = null
        val array: ArrayList<JukeboxInfo> = arrayListOf()
        val success = exponentiallyBackoff(16000, 8) {
            log("Attempting to search Spotify for \"$query\"")
            val (request, response, r) = tokenLock.withLock { Fuel.get("https://api.spotify.com/v1/search", listOf("q" to query, "type" to "track")).bearer(token).responseString() }
            val mapResponse = EternalJukebox.jsonMapper.readValue(response.data, Map::class.java)

            when (response.httpStatusCode) {
                200 -> {
                    ((mapResponse["tracks"] as Map<*, *>)["items"] as List<*>).filter { it is Map<*, *> && it.containsKey("id") }.map {
                        val track = it as Map<*, *>
                        array.add(JukeboxInfo("SPOTIFY", track["id"] as String, track["name"] as String, track["name"] as String, ((track["artists"] as List<*>).first() as Map<*, *>)["name"] as String, "https://open.spotify.com/track/${track["id"] as String}"))
                    }
                    return@exponentiallyBackoff false
                }
                400 -> {
                    if (((mapResponse["error"] as Map<*, *>)["message"] as String) == "Only valid bearer authentication supported") {
                        log("Got back response code 400  with error \"Only valid bearer authentication supported\"; reloading token, backing off, and trying again")
                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        log("Got back response code 400 with data ${String(response.data)}; returning INVALID_SEARCH_DATA")
                        error = SpotifyError.INVALID_SEARCH_DATA
                        return@exponentiallyBackoff false
                    }
                }
                401 -> {
                    log("Got back response code 401  with data ${String(response.data)}; reloading token, backing off, and trying again")
                    reload()
                    return@exponentiallyBackoff true
                }
                else -> {
                    log("Got back response code ${response.httpStatusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && error == null

        if(success)
            log("Successfully searched for \"$query\"")
        else
            log("Failed to search for \"query\". Error: $error")

        return array.toTypedArray()
    }

    override fun analyse(id: String): JukeboxTrack {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun reload(): SpotifyError? {
        tokenLock.withLock {
            var error: SpotifyError? = null
            val success = exponentiallyBackoff(64000, 8) {
                log("Attempting to reload Spotify Token")
                val (request, response, r) =
                        Fuel.post("https://accounts.spotify.com/api/token").body("grant_type=client_credentials").authenticate(EternalJukebox.config.spotifyClient ?: run {
                            error = SpotifyError.NO_AUTH_DETAILS
                            return@exponentiallyBackoff false
                        }, EternalJukebox.config.spotifySecret ?: run {
                            error = SpotifyError.NO_AUTH_DETAILS
                            return@exponentiallyBackoff false
                        }).responseString()

                when (response.httpStatusCode) {
                    200 -> {
                        token = JsonObject(r.component1()).getString("access_token")
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
                        log("Got back response code ${response.httpStatusCode} with data ${String(response.data)}; backing off and trying again")
                        return@exponentiallyBackoff true
                    }
                }
            } && error == null

            if(!success)
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