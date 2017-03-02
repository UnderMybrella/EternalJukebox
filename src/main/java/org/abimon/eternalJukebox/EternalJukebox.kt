package org.abimon.eternalJukebox

import com.mashape.unirest.http.Unirest
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.StaticHandler
import org.abimon.visi.io.HTTPDataSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.*

val eternalDir = File("eternal")
val songsDir = File("songs")
val logDir = File("logs")
val ipAddr = "http://${Unirest.get("http://ipecho.net/plain").asString().body}:11037/"

fun main(args: Array<String>) {
    if(!eternalDir.exists())
        eternalDir.mkdir()
    if(!songsDir.exists())
        songsDir.mkdir()
    if(!logDir.exists())
        logDir.mkdir()

    val vertx = Vertx.vertx()
    val http = vertx.createHttpServer()

    val router = Router.router(vertx)

    router.route().handler(CorsHandler.create("*"))
    router.route("/eternal/id").blockingHandler(::id)
    router.route("/song").blockingHandler(::song)
    router.route("/files/*").handler(StaticHandler.create("files"))
    router.route("/index.html").handler { context -> context.response().sendFile("index.html") }
    router.route("/favicon.ico").handler { context -> context.response().sendFile("files/favicon.png")}
    router.route().handler { context -> println(context.request().path()) }

    http.requestHandler { router.accept(it) }.listen(11037)
    println("Listening at $ipAddr")
}

var bearer = ""
var expires = Instant.now()

fun song(context: RoutingContext) {
    val request = context.request()

    val id = request.getParam("id").replace("[^A-Za-z0-9]".toRegex(), "")
    val file = File(songsDir, "$id.mp3")
    if(file.exists())
        context.response().sendFile(file.absolutePath)
    else {
        val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").asJson()
        if(trackInfo.status != 200) {
            context.response().setStatusCode(404).end()
            return
        }
        val trackInfoBody = trackInfo.body.`object`

        val name = trackInfoBody["name"]
        val artist = ((trackInfoBody["artists"] as JSONArray)[0] as JSONObject)["name"]
        val results = searchYoutube("$artist - $name")
        if(results.isEmpty()) {
            println("Error: [$artist - $name] produced *no* results (See? $results)")
            context.response().setStatusCode(404).end()
            return
        }
        val process = ProcessBuilder()
                .command("bash", "yt.sh", "https://youtu.be/${results[0]}", file.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(File(logDir, "$id.log"))
                .start()

        process.waitFor()
        if(file.exists())
            context.response().sendFile(file.absolutePath)
        else
            context.response().setStatusCode(404).end()
    }
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

        val trackInfo = Unirest.get("https://api.spotify.com/v1/tracks/$id").asJson()
        if(trackInfo.status != 200) {
            context.response().setStatusCode(404).end()
            return
        }
        val acousticInfo = Unirest.get("https://api.spotify.com/v1/audio-analysis/$id").header("Authorization", "Bearer $bearer").asJson()
        if(trackInfo.status != 200) {
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