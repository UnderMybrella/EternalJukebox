package org.abimon.eternalJukebox

import com.mashape.unirest.http.Unirest
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CSRFHandler
import org.abimon.eternalJukebox.objects.*
import org.abimon.notifly.notification
import org.abimon.visi.lang.ByteUnit
import org.abimon.visi.lang.asOptional
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

object API {
    val csrfHandler: CSRFHandler by lazy { CSRFHandler.create(config.csrfSecret) }
    val bodyHandler: BodyHandler by lazy { BodyHandler.create(tmpUploadDir.name).setDeleteUploadedFilesOnEnd(true).setBodyLimit(if (config.uploads) 10 * 1000 * 1000 else 500 * 1000) }
    val b64Decoder = Base64.getUrlEncoder()
    val b64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val b64CustomID = "CSTM[$b64Alphabet]+".toRegex()
    val temporalComponents = arrayOf(ChronoUnit.SECONDS, ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS)

    fun handleCSRF(ctxt: RoutingContext) {
        if (!(ctxt.data()["${config.eternityUserKey}-Auth"] as? Boolean ?: false))
            csrfHandler.handle(ctxt)
        else
            ctxt.next()
    }

    fun searchSpotify(context: RoutingContext) {
        val request = context.request()

        //if(config.disableSearch)

        if (!config.spotifyBase64.isPresent) {
            context.response().setStatusCode(501).end(JSONObject().put("error", "[Search] Server not configured for new Spotify requests; bug the administrator"))
            return
        }

        if (expires.isBefore(Instant.now()))
            reloadSpotifyToken()

        val returnSummary = request.getParam("summary")?.toBoolean() ?: false
        val query = request.getParam("q") ?: "Never Gonna Give You Up"
        val limit = request.getParam("results") ?: "30"
        var lastStatusCode: Int = -1

        loop@ for (i in 0 until 3) {
            try {
                val array = JSONArray()

                val trackInfo = Unirest.get("https://api.spotify.com/v1/search")
                        .queryString("q", query)
                        .queryString("type", "track")
                        .queryString("limit", limit)
                        .header("Authorization", "Bearer $bearer")
                        .asJson()

                lastStatusCode = trackInfo.status

                when (lastStatusCode) {
                    400 -> {
                        println("[Search] Spotify knocked back our request due to status code 400 ${trackInfo.statusText}")
                        continue@loop
                    }
                    401 -> {
                        println("[Search] Reloading Spotify Token due to status code 401 ${trackInfo.statusText}")
                        reloadSpotifyToken()
                        continue@loop
                    }
                    else -> {
                        if (trackInfo.status >= 300) {
                            context.response().setStatusCode(trackInfo.status).end(JSONObject().put("error", "[Search] Spotify responded with a status code of $lastStatusCode and an error of ${trackInfo.statusText}"))
                            return
                        }
                    }
                }

                for (item in trackInfo.body.`object`.getJSONObject("tracks").getJSONArray("items")) {
                    val obj = item as JSONObject
                    val track = JSONObject()

                    track.put("id", obj["id"])
                    track.put("name", obj["name"])
                    track.put("title", obj["name"]) //Just in case
                    track.put("artist", ((obj["artists"] as JSONArray)[0] as JSONObject)["name"])
                    track.put("url", "/api/song/${obj["id"]}")

                    if (returnSummary) {
                        val (eternalInfo, status) = trackInfoForID(obj["id"] as String)
                        if (eternalInfo != null)
                            track.put("audio_summary", JSONObject(eternalInfo.audio_summary))
                    }

                    array.put(track)
                }

                context.response().end(array)
                return
            } catch(json: JSONException) {
                error("An error occurred while parsing JSON for search: $json")
                continue
            }
        }

        when (lastStatusCode) {
            400 -> {
                println("[Search] Last status code was a 400; Either Spotify's changed their API again or there's an issue with our bearer token? Curious. Check previous lines in this log")
                context.response().setStatusCode(503).end(JSONObject().put("error", "[Search] Last status code was a 400; reason unknown"))
            }
            401 -> {
                println("[Search] Last status code was a 401; Spotify credentials are wrong? Returning a 503")
                context.response().setStatusCode(503).end(JSONObject().put("error", "[Search] Last status code was a 401; reason unknown"))
            }
            else -> context.response().setStatusCode(500).end(JSONObject().put("error", "[Search] Last status code was a $lastStatusCode; reason unknown"))
        }
    }

    fun trackInformation(context: RoutingContext) {
        val id = context.pathParam("id").replace("[^A-Za-z0-9]".toRegex(), "")

        val (eternal, status) = trackInfoForID(id)
        if (eternal != null)
            context.ifDataNotCached(JSONObject(eternal).toString()) { response().jsonContent().sendCachedData(it) }
        else {
            if (!config.spotifyBase64.isPresent)
                context.response().setStatusCode(501).end(JSONObject().put("error", "[Track Info] Server not configured for new Spotify requests; bug the administrator"))
            else if (status != null) {
                when (status.first) {
                    404 -> context.response().setStatusCode(404).end(JSONObject().put("error", "[Track Info] No track could be found for ID $id"))
                    else -> context.response().setStatusCode(500).end(JSONObject().put("error", "[Track Info] Unknown error from Spotify for ID $id: ${status.first} ${status.second}"))
                }
            } else
                context.response().setStatusCode(500).end(JSONObject().put("error", "[Track Info] Unknown error; no error code generated"))
        }
    }

    fun externalAudio(context: RoutingContext) {
        val request = context.request()

        val url = request.getParam("url") ?: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        if (url.matches(b64CustomID)) {
            val file = File(audioDir, "$url.${config.format}")
            if (file.exists()) {
                context.ifFileNotCached(file) { response().sendCachedFile(it) }
                return
            }
        }

        val b64 = b64Encoder.encodeToString(url.toByteArray(Charsets.UTF_8))
        val file = File(audioDir, "$b64.${config.format}")

        if (file.exists()) {
            context.ifFileNotCached(file) { response().sendCachedFile(it) }
            return
        }

        checkStorage()
        val process = ProcessBuilder()
                .command("bash", "yt.sh", url, file.absolutePath, config.format)
                .redirectErrorStream(true)
                .redirectOutput(File(logDir, "$b64.log"))
                .start()

        process.waitFor()
        if (file.exists())
            context.response().sendFile(file.absolutePath)
        else {
            val (fallback, status) = defaultSongForID(request.getParam("fallback") ?: "")
            if (fallback != null)
                context.ifFileNotCached(fallback) { response().sendCachedFile(it) }
            else
                context.response().setStatusCode(404).end()
        }
    }

    fun defaultSong(context: RoutingContext) {
        val id = context.pathParam("id").replace("[^A-Za-z0-9]".toRegex(), "")
        val (songFile, status) = defaultSongForID(id)
        if (songFile != null)
            context.ifFileNotCached(songFile) { response().sendCachedFile(it) }
        else
            context.response().setStatusCode(404).end()
    }

    fun popularTracksForService(context: RoutingContext) {
        val request = context.request()
        val service = context.pathParam("service")?.toLowerCase() ?: "jukebox"
        val popular = run {
            when (service) {
                "jukebox" -> return@run getPopularJukeboxSongs((request.getParam("count") ?: "30").toIntOrNull() ?: 30)
                else -> emptyList<String>()
            }
        }

        val array = JSONArray()
        popular.forEach { id ->
            val (trackInfo) = trackInfoForID(id)
            if (trackInfo != null) {
                val track = JSONObject()

                track.put("id", trackInfo.info.id)
                track.put("name", trackInfo.info.name)
                track.put("title", trackInfo.info.name) //Just in case
                track.put("artist", trackInfo.info.artist)
                track.put("url", trackInfo.info.url)

                array.put(track)
            }
        }

        context.response().end(array)
    }

    fun expand(context: RoutingContext) {
        val id = context.pathParam("id") ?: "none"
        val params = expand(id)
        if (params != null) {
            val paramsMap = params.split("&").map { it.split("=") }.filter { it.size == 2 }.map { Pair(it[0], it[1]) }.toMap(HashMap())
            val service = paramsMap.remove("type") ?: "jukebox"
            val response = JSONObject()
            when (service.toLowerCase()) {
                "jukebox" -> response.put("url", "/jukebox_go.html?${paramsMap.entries.joinToString("&") { (k, v) -> "$k=$v" }}")
                "canonizer" -> response.put("url", "/canonizer_go.html?${paramsMap.entries.joinToString("&") { (k, v) -> "$k=$v" }}")
                else -> response.put("url", "/jukebox_index.html")
            }

            val (info) = trackInfoForID(paramsMap.remove("id") ?: "4uLU6hMCjMI75M1A2tKUQC")
            if (info != null)
                response.put("info", JSONObject(info.info))
            context.response().jsonContent().end(response.toString())
        } else
            context.response().setStatusCode(404).end(JSONObject().put("error", "No short ID found for $id"))
    }

    fun expandAndRedirect(context: RoutingContext) {
        val params = expand(context.pathParam("id") ?: "none")
        if (params != null) {
            val paramsMap = params.split("&").map { it.split("=") }.filter { it.size == 2 }.map { Pair(it[0], it[1]) }.toMap(HashMap())
            val service = paramsMap.remove("type") ?: "jukebox"
            when (service.toLowerCase()) {
                "jukebox" -> context.response().redirect("/jukebox_go.html?${paramsMap.entries.joinToString("&") { (k, v) -> "$k=$v" }}")
                "canonizer" -> context.response().redirect("/canonizer_go.html?${paramsMap.entries.joinToString("&") { (k, v) -> "$k=$v" }}")
                else -> context.response().redirect("/jukebox_index.html")
            }
        } else
            context.response().redirect("/jukebox_index.html")
    }

    fun shrink(context: RoutingContext) = context.response().end(JSONObject().put("id", getOrShrinkParams(context.bodyAsString)))
    fun uploadAudio(context: RoutingContext) {
        if (context.fileUploads().isNotEmpty()) {
            val file = context.fileUploads().first()
            val id = "CSTM${b64Encoder.encodeToString(file.uploadedFileName().toByteArray(Charsets.UTF_8))}"
            val song = File(audioDir, "$id.${config.format}")

            val process = ProcessBuilder()
                    .command("ffmpeg", "-i", file.uploadedFileName(), song.absolutePath)
                    .redirectErrorStream(true)
                    .redirectOutput(File(logDir, "$id.log"))
                    .start()

            process.waitFor()
            if (song.exists())
                context.response().end(id)
            else
                context.response().setStatusCode(404).end(JSONObject().put("error", "[Upload Audio] yt-dl didn't create a file"))
        } else
            context.response().setStatusCode(400).end(JSONObject().put("error", "[Upload Audio] No file upload provided"))
    }

    fun trackInfoForID(id: String): ErroredResponse<EternalInfo?, HttpStatus?> {
        val file = File(eternalDir, "$id.json")
        if (file.exists())
            return objMapper.readValue(file, EternalInfo::class.java) withHttpError null
        else {
            if (!config.spotifyBase64.isPresent)
                return ErroredResponse(null, 501 to "Missing Spotify Details")

            checkStorage()
            if (expires.isBefore(Instant.now()))
                reloadSpotifyToken()

            for (i in 0 until 3) {
                try {
                    val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").header("Authorization", "Bearer $bearer").asJson()
                    if (trackInfo.status != 200) {
                        println("[Track Info] Non 200 status code from Spotify for ID $id (Code: ${trackInfo.status}, error ${trackInfo.statusText}) for ID $id and iteration $i")
                        return ErroredResponse(null, trackInfo.statusPair)
                    }

                    val acousticInfo = Unirest.get("https://api.spotify.com/v1/audio-analysis/$id").header("Authorization", "Bearer $bearer").asJson()
                    if (acousticInfo.status != 200) {
                        println("[Track Info -> Acoustic Info] Non 200 status code from Spotify (Code: ${acousticInfo.status}, error ${acousticInfo.statusText}) for ID $id and iteration $i")
                        return ErroredResponse(null, trackInfo.statusPair)
                    }

                    val trackInfoBody = trackInfo.body mapTo SpotifyTrack::class ?: return ErroredResponse(null, trackInfo.statusPair)
                    val track = acousticInfo.body mapTo SpotifyAudio::class ?: return ErroredResponse(null, trackInfo.statusPair)

                    val eternalInfo = EternalInfo(
                            EternalMetadata(
                                    trackInfoBody.id,
                                    trackInfoBody.name,
                                    trackInfoBody.name,
                                    trackInfoBody.artists[0].name,
                                    "/api/song/$id"
                            ),
                            EternalAnalysis(
                                    track.sections,
                                    track.bars,
                                    track.beats,
                                    track.tatums,
                                    track.segments
                            ),
                            track.track
                    )

                    objMapper.writeValue(FileOutputStream(file), eternalInfo)
                    return eternalInfo withHttpError null
                } catch(th: Throwable) {
                    error("[Track Info] An unexpected error occurred: $th")
                }
            }
        }

        return ErroredResponse(null, null)
    }

    fun defaultSongForID(id: String): ErroredResponse<File?, HttpStatus?> {
        val file = File(songsDir, "$id.${config.format}")
        if (file.exists())
            return file withHttpError null
        else {
            checkStorage()
            for (i in 0 until 3) {
                try {
                    val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").header("Authorization", "Bearer $bearer").asJson()
                    if (trackInfo.status != 200) {
                        println("[Default Song For ID] Non 200 code for song for $id; iteration $i")
                        continue
                    }
                    val track = trackInfo.body mapTo SpotifyTrack::class ?: return ErroredResponse(null, trackInfo.statusPair)

                    val name = track.name
                    val artist = track.artists[0].name
                    val duration = track.duration_ms
                    val results = searchYoutube("$artist - $name")
                    if (results.isEmpty()) {
                        error("[Default Song For ID] YT Search for [$artist - $name] produced *no* results (See? ${results.joinToString()})")
                        return ErroredResponse(null, 404 to "No YT search results")
                    }

                    val closestResult = if (true) results[0].id else results.sortedWith(Comparator<YoutubeVideo> { (_, duration1), (_, duration2) -> Math.abs(duration - duration1.toMillis()).compareTo(Math.abs(duration - duration2.toMillis())) }).first().id
                    val process = ProcessBuilder()
                            .command("bash", "yt.sh", "https://youtu.be/$closestResult", file.absolutePath, config.format)
                            .redirectErrorStream(true)
                            .redirectOutput(File(logDir, "$id.log"))
                            .start()

                    if (process.waitFor(50, TimeUnit.SECONDS)) {
                        if (file.exists())
                            return file withHttpError null
                        else
                            return ErroredResponse(null, 500 to "File doesn't exist")
                    } else
                        return ErroredResponse(null, 504 to "yt-dl timed out")
                } catch(json: JSONException) {
                    error("[Default Song For ID] An error occured while parsing JSON for song of id $id: $json")
                    continue
                }
            }
        }

        return ErroredResponse(null, null)
    }

    fun searchYoutube(search: String): Array<YoutubeVideo> {
        val html = Jsoup.parse(Unirest.get("https://www.youtube.com/results").queryString("search_query", URLEncoder.encode(search, "UTF-8")).asString().body)
        val listElements = html.select("ol.item-section").select("li").map { li -> li.select("div.yt-lockup-dismissable") }
        val videos = ArrayList<YoutubeVideo>(listElements.size)
        listElements.forEach { ytVid ->
            val id = ytVid.select("a[href]").attr("href")
            if (id.isNotBlank()) {
                val ytID = id.substringAfter("=").substring(0, 11)
                val videoTime = ytVid.select("span.video-time").singleOrNull()?.text() ?: "1:00"
                var duration = Duration.ZERO
                videoTime.split(':').reversed().forEachIndexed { index, s -> duration = duration.plus(s.toLong(), temporalComponents[index]) }
                videos.add(YoutubeVideo(ytID, duration))
            }
        }
        return videos.toTypedArray()
    }

    fun checkStorage() {
        var totalUsed = 0L
        val allFiles = ArrayList<File>()

        allFiles.addAll(eternalDir.listFiles().filter(File::isFile))
        allFiles.addAll(songsDir.listFiles().filter(File::isFile))
        allFiles.addAll(audioDir.listFiles().filter(File::isFile))

        allFiles.sortedWith(Comparator<File> { file1, file2 -> file1.lastModified().compareTo(file2.lastModified()) })
        allFiles.forEach { file -> totalUsed += file.length() }

        if (totalUsed > config.storageEmergency) {
            sendFirebaseMessage(notification {
                title("[EternalJukebox] Emergency Storage Reached")
                body("EternalJukebox has $totalUsed B used (${ByteUnit(totalUsed).toMegabytes()} MB)")
            }.asOptional())
        }

        if (totalUsed > config.storageSize) {
            allFiles.forEach { file ->
                if (totalUsed > config.storageBuffer) {
                    println("$totalUsed > ${config.storageBuffer}")
                    totalUsed -= file.length()
                    file.delete()
                    println("Deleted $file")
                }
            }
        }
    }

    fun setup(router: Router) {
        if (config.csrf) {
            router.get().handler(csrfHandler)
            router.route("/api/profile/*").handler(API::handleCSRF)
        }

        router.get("/api/audio").handler(API::externalAudio)
        router.get("/api/search").handler(API::searchSpotify)
        router.get("/api/popular/:service").handler(API::popularTracksForService)

        router.get("/api/info/:id").handler(API::trackInformation)
        router.get("/api/song/:id").handler(API::defaultSong)
        router.get("/api/expand/:id").handler(API::expand)
        router.get("/api/expand/:id/redirect").handler(API::expandAndRedirect)

        router.post("/api/shrink").handler(bodyHandler)
        router.post("/api/shrink").handler(API::shrink)

        if (config.uploads) {
            router.post("/api/upload/*").handler {
                checkStorage()
                it.next()
            }
            router.post("/api/upload/*").handler(bodyHandler)
            router.post("/api/upload/audio").blockingHandler(API::uploadAudio)
        }

        config.redirects.forEach { from, to -> router.get(from).handler { ctxt -> ctxt.response().redirect(to) } }
    }
}