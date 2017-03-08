package org.abimon.eternalJukebox

import com.mashape.unirest.http.Unirest
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.StaticHandler
import org.abimon.visi.io.HTTPDataSource
import org.abimon.visi.lang.asOptional
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.*

val eternalDir = File("eternal")
val songsDir = File("songs")
val audioDir = File("audio")
val logDir = File("logs")
val ipAddr = "http://${Unirest.get("http://ipecho.net/plain").asString().body}:11037/"

fun main(args: Array<String>) {
    if(!eternalDir.exists())
        eternalDir.mkdir()
    if(!songsDir.exists())
        songsDir.mkdir()
    if(!audioDir.exists())
        audioDir.mkdir()
    if(!logDir.exists())
        logDir.mkdir()

    val vertx = Vertx.vertx()
    val http = vertx.createHttpServer()

    val router = Router.router(vertx)

    router.route().handler(CorsHandler.create("*"))
    router.route("/search").blockingHandler(::search)
    router.route("/eternal/id").blockingHandler(::id)
    router.route("/song").blockingHandler(::song)
    router.route("/audio").blockingHandler(::audio)
    router.route("/files/*").handler(StaticHandler.create("files"))
    router.route("/index.html").handler { context -> context.response().sendFile("index.html") }
    router.route("/faq.html").handler { context -> context.response().sendFile("faq.html") }
    router.route("/popular_tracks").handler(::popular)
    router.route("/favicon.ico").handler { context -> context.response().sendFile("files/favicon.png")}
    router.route().handler { context -> println(context.request().path()) }

    http.requestHandler { router.accept(it) }.listen(11037)
    println("Listening at $ipAddr")
}

var bearer = ""
var expires: Instant = Instant.now()

fun popular(context: RoutingContext) {
    val request = context.request()
}

fun search(context: RoutingContext) {
    val request = context.request()

    if (expires.isBefore(Instant.now()))
        reloadToken()

    for(i in 0 until 3) {
        try {
            val array = JSONArray()

            val trackInfo = Unirest.get("https://api.spotify.com/v1/search")
                    .queryString("q", request.getParam("q") ?: "Never Gonna Give You Up")
                    .queryString("type", "track")
                    .queryString("limit", request.getParam("results") ?: "30")
                    .asJson()

            for(item in trackInfo.body.`object`.getJSONObject("tracks").getJSONArray("items")) {
                val obj = item as JSONObject
                val track = JSONObject()

                track.put("id", obj["id"])
                track.put("name", obj["name"])
                track.put("title", obj["name"]) //Just in case
                track.put("artist", ((obj["artists"] as JSONArray)[0] as JSONObject)["name"])
                track.put("url", "${ipAddr}song?id=${obj["id"]}")

                array.put(track)
            }

            context.response().putHeader("Content-Type", "application/json").end(array.toString())
            break
        }
        catch(json: JSONException) {
            println("An error occurred while parsing JSON: $json")
            continue
        }
    }
}

fun song(context: RoutingContext) {
    val request = context.request()

    val id = request.getParam("id").replace("[^A-Za-z0-9]".toRegex(), "")
    val song = songForID(id)
    if(song.isPresent)
        context.response().sendFile(song.get().absolutePath)
    else
        context.response().setStatusCode(404).end()
}

fun songForID(id: String): Optional<File> {
    val file = File(songsDir, "$id.mp3")
    if(file.exists())
        return file.asOptional()
    else {
        for(i in 0 until 3) {
            try {
                val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").asJson()
                if (trackInfo.status != 200)
                    continue
                val trackInfoBody = trackInfo.body.`object`

                val name = trackInfoBody["name"]
                val artist = ((trackInfoBody["artists"] as JSONArray)[0] as JSONObject)["name"]
                val results = searchYoutube("$artist - $name")
                if (results.isEmpty()) {
                    println("Error: [$artist - $name] produced *no* results (See? $results)")
                    return Optional.empty()
                }
                val process = ProcessBuilder()
                        .command("bash", "yt.sh", "https://youtu.be/${results[0]}", file.absolutePath)
                        .redirectErrorStream(true)
                        .redirectOutput(File(logDir, "$id.log"))
                        .start()

                process.waitFor()
                if (file.exists())
                    return file.asOptional()
                else
                    return Optional.empty()
            }
            catch(json: JSONException) {
                println("An error occured while parsing JSON: $json")
                continue
            }
        }
    }

    return Optional.empty()
}

fun searchYoutube(search: String): Array<String> {
    try {
        val videoIDs = LinkedList<String>()
        var results = false
        for (line in String(HTTPDataSource(URL("https://www.youtube.com/results?search_query=" + URLEncoder.encode(search, "UTF-8"))).getData(), Charsets.UTF_8).split("\n".toRegex())) {
            if (line.contains("<div class=\"branded-page-box search-pager  spf-link \">"))
                break
            if (line.contains("\"item-section\""))
                results = true

            if (results && line.trim({ it <= ' ' }).startsWith("<li>")) {
                val start = line.indexOf("href=\"/watch?v=") + "href=\"/watch?v=".length
                val videoID = line.substring(start, start + 11)
                videoIDs.add(videoID)
            }
        }
        return videoIDs.toTypedArray()
    } catch (th: Throwable) {
        th.printStackTrace()
    }

    return arrayOf("")
}

fun audio(context: RoutingContext) {
    val request = context.request()

    val url = request.getParam("url") ?: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    val b64 = Base64.getEncoder().encodeToString(url.toByteArray(Charsets.UTF_8)).replace("/", "-")
    val file = File(audioDir, "$b64.mp3")

    println(url)

    val process = ProcessBuilder()
            .command("bash", "yt.sh", url, file.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(File(logDir, "$b64.log"))
            .start()

    process.waitFor()
    if (file.exists())
        context.response().sendFile(file.absolutePath)
    else {
        val fallback = songForID(request.getParam("fallback") ?: "")
        if(fallback.isPresent)
            context.response().sendFile(fallback.get().absolutePath)
        else
            context.response().setStatusCode(404).end()
    }
}

/** Wants track information and acoustics */
fun id(context: RoutingContext) {
    val request = context.request()

    val id = request.getParam("id").replace("[^A-Za-z0-9]".toRegex(), "")
    val file = File(eternalDir, "$id.json")
    if(file.exists())
        context.response().sendFile(file.absolutePath)
    else {
        if (expires.isBefore(Instant.now()))
            reloadToken()

        for(i in 0 until 3) {
            try {
                val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").asJson()
                if (trackInfo.status != 200) {
                    context.response().setStatusCode(404).end()
                    return
                }
                val acousticInfo = Unirest.get("https://api.spotify.com/v1/audio-analysis/$id").header("Authorization", "Bearer $bearer").asJson()
                if (trackInfo.status != 200) {
                    context.response().setStatusCode(trackInfo.status).end()
                    return
                }
                val trackInfoBody = trackInfo.body.`object`
                val track = acousticInfo.body.`object`

                val eternal = JSONObject()

                val info = JSONObject()
                info.put("id", trackInfoBody["id"])
                info.put("name", trackInfoBody["name"])
                info.put("title", trackInfoBody["name"]) //Just in case
                info.put("artist", ((trackInfoBody["artists"] as JSONArray)[0] as JSONObject)["name"])
                info.put("url", "${ipAddr}song?id=$id")
                eternal.put("info", info)

                val analysis = JSONObject()
                analysis.put("sections", track["sections"])
                analysis.put("bars", track["bars"])
                analysis.put("beats", track["beats"])
                analysis.put("tatums", track["tatums"])
                analysis.put("segments", track["segments"])
                eternal.put("analysis", analysis)

                eternal.put("audio_summary", track["track"])

                file.writeText(eternal.toString())
                context.response().sendFile(file.absolutePath)
                break
            }
            catch(json: JSONException) {
                println("An error occurred while parsing JSON: $json")
                continue
            }
            catch(th: Throwable) {
                println("An unexpected error occurred: $th")
            }
        }
    }
}

fun reloadToken() {
    val token = Unirest
            .post("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic Mjc2MTcwOWZkOTMzNDRmZDg0OWJkMTUyZjQ0NDViNDk6YmVkZDMyNjIxMjZiNDQzY2IwOTM3MTFiZTRjZTMwN2Y=")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body("grant_type=client_credentials")
            .asJson()
            .body.`object`
    bearer = token["access_token"] as String
    expires = Instant.ofEpochMilli(System.currentTimeMillis() + (token["expires_in"] as Int).times(1000))
}