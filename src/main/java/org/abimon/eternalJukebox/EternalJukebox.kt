package org.abimon.eternalJukebox

import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.handler.StaticHandler
import org.abimon.visi.io.HTTPDataSource
import org.abimon.visi.lang.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

typealias SSLCertPair = Optional<Pair<String, String>>

data class JukeboxConfig(
        var ip: String = "http://\$ip:\$port", var cors: Boolean = true, var ssl: SSLCertPair = Optional.empty(),
        var spotifyBase64: Optional<String> = Optional.empty(),
        var searchEndpoint: Optional<String> = "/search".asOptional(),
        var idEndpoint: Optional<String> = "/id".asOptional(),
        var audioEndpoint: Optional<String> = "/audio".asOptional(),
        var songEndpoint: Optional<String> = "/song".asOptional(),
        var fileManager: Optional<Pair<String, String>> = Pair("/files/*", "files").asOptional(),
        var indexEndpoint: Optional<String> = "/index.html".asOptional(),
        var faqEndpoint: Optional<String> = "/faq.html".asOptional(),
        var popularTracksEndpoint: Optional<String> = "/popular_tracks".asOptional(),
        var faviconPath: Optional<String> = "files/favicon.png".asOptional(),
        var logAllPaths: Boolean = false,
        var logMissingPaths: Boolean = false,
        var port: Int = 11037,

        var storeSongInformation: Boolean = true,
        var storeSongs: Boolean = true,
        var storeAudio: Boolean = true,

        var redirects: Map<String, String> = HashMap(),

        /** Here come lazy settings */
        var spotifyClient: Optional<String> = Optional.empty(),
        var spotifySecret: Optional<String> = Optional.empty()
)

val eternalDir = File("eternal")
val songsDir = File("songs")
val audioDir = File("audio")
val logDir = File("logs")

val configFile = File("config.json")
val projectHosting = "https://github.com/UnderMybrella/EternalJukebox" //Just in case this changes or needs to be referenced

var config = JukeboxConfig()

fun getIP(): String {
    try{
        Unirest.get("https://google.com").asString().body
    }
    catch(e: UnirestException) {
        println("The internet's offline. Or something *really* bad has happened: $e")
        return "localhost"
    }

    for(web in arrayOf("http://ipecho.net/plain", "http://icanhazip.com", "http://ipinfo.io/ip",
            "https://ident.me/", "http://checkip.amazonaws.com", "http://smart-ip.net/myip",
            "http://bot.whatismyipaddress.com", "https://secure.informaction.com/ipecho/", "http://curlmyip.com")) {
        try {
            return Unirest.get(web).asString().body
        }
        catch(e: UnirestException) {
            println("$web is offline, trying the next one...")
        }
    }

    return "localhost"
}

fun <T: Any> mapToMutablePojo(json: JSONObject, pojo: KClass<T>, obj: T) {
    json.keys().forEach { key ->
        pojo.memberProperties
                .filterIsInstance(KMutableProperty::class.java)
                .firstOrEmpty { property -> property.name == key }
                .ifPresent { property ->
                    val type = property.returnType.classifier
                    try {
                        when (type) {
                            String::class -> property.setter.call(obj, "${json[key]}")
                            Optional::class -> {
                                val optionalType = property.returnType.arguments[0].type?.classifier

                                when (optionalType) {
                                    String::class -> property.setter.call(obj, if("${json[key]}".isBlank()) Optional.empty<String>() else "${json[key]}".asOptional())
                                    Pair::class -> {
                                        /** We only deal with String pairs here, so no need for type checks */
                                        val pair = json[key]
                                        if (pair is JSONArray) { //This. This is what we want
                                            if(pair.length() == 0)
                                                property.setter.call(obj, Optional.empty<Pair<String, String>>())
                                            else if (pair.length() == 1) //Ehh, not so much
                                                property.setter.call(obj, Pair("${pair.firstOrNull() ?: ""}", "").asOptional())
                                            else
                                                property.setter.call(obj, Pair("${pair[0]}", "${pair[1]}").asOptional()) //Ignore anything else
                                        } else if (pair is JSONObject) { //This is tolerable?
                                            if (pair.length() == 0)
                                                property.setter.call(obj, Optional.empty<Pair<String, String>>())
                                            else if (pair.has("first")) {
                                                if (pair.has("second"))
                                                    property.setter.call(obj, Pair("${pair["first"]}", "${pair["second"]}").asOptional())
                                                else
                                                    property.setter.call(obj, Pair("${pair["first"]}", "").asOptional())
                                            } else if (pair.has("second"))
                                                property.setter.call(obj, Pair("${pair["first"]}", "").asOptional())
                                            else if (pair.length() < 2)
                                                property.setter.call(obj, Pair("${pair[pair.names()[0] as String]}", "").asOptional())
                                            else
                                                property.setter.call(obj, Pair("${pair[pair.names()[0] as String]}", "${pair[pair.names()[1] as String]}").asOptional())
                                        } else if (pair is String) { //W h y
                                            if (pair.contains('|') && pair.split('|', limit = 2).size == 2)
                                                property.setter.call(obj, Pair(pair.split('|', limit = 2)[0], pair.split('|', limit = 2)[1]).asOptional())
                                            else if(pair.isEmpty())
                                                property.setter.call(obj, Optional.empty<Pair<String, String>>())
                                            else
                                                property.setter.call(obj, Pair(pair, "").asOptional())
                                        }
                                    }
                                    else -> {
                                        println(optionalType)
                                    }
                                }
                            }
                            Boolean::class -> property.setter.call(obj, json[key] as Boolean)
                            Int::class -> property.setter.call(obj, json[key] as Int)
                            Map::class -> { //We only have the one map here
                                val jsonVal = json[key]
                                if(jsonVal is JSONObject) {
                                    val map = HashMap<String, String>()
                                    map.putAll(jsonVal.names().map { name -> name as String }.map { name -> Pair(name, "${jsonVal[name]}") }.toTypedArray())
                                    property.setter.call(obj, map)
                                }
                            }
                            else -> println("$key is a ${(type as KClass<*>).simpleName}")
                        }
                    } catch(wrongClass: ClassCastException) {
                        println("Whoops, you tried to use the wrong type of object for $key. You used a ${json[key]::class.simpleName} whereas we needed a ${(type as KClass<*>).simpleName}")
                    } catch(hack: Exception) {
                        val url = uploadGist("Exception attempting to set key $key to $property", hack.exportStackTrace())
                        if(url.isPresent)
                            println("An error occurred while loading the configuration file. Go file an issue here ($projectHosting) with this URL: ${url()}")
                        else
                            println("An error occurred while loading the configuration file. Go file an issue here ($projectHosting) with this stacktrace: ${hack.exportStackTrace()}")
                    }
                }
    }
}

fun uploadGist(desc: String, content: String, name: String = "error.txt"): Optional<String> {
    val json = JSONObject()
    json.put("description", desc)
    json.put("public", true)

    val files = JSONObject()
    val file = JSONObject()
    file.put("content", content)
    files.put(name, file)
    json.put("files", files)

    val response = Unirest.post("https://api.github.com/gists")
            .header("Content-Type", "application/json")
            .header("Accept", "application/vnd.github.v3+json")
            .body(json.toString()).asString()

    when(response.status) {
        201 -> return (JSONObject(response.body)["html_url"] as String).asOptional()
        else -> {
            println("Gist failed with error ${response.status}: ${response.body}")
            return Optional.empty()
        }
    }
}

fun main(args: Array<String>) {
    if (!eternalDir.exists())
        eternalDir.mkdir()
    if (!songsDir.exists())
        songsDir.mkdir()
    if (!audioDir.exists())
        audioDir.mkdir()
    if (!logDir.exists())
        logDir.mkdir()

    if (configFile.exists()) {
        try {
            val json = JSONObject(configFile.readText(Charsets.UTF_8))
            mapToMutablePojo(json, JukeboxConfig::class, config)
        }
        catch(jsonError: JSONException) {
            println("Something went wrong reading the config file ($configFile): $jsonError")
        }
    } else
        println("Config file ($configFile) does not exist, using default values (see $projectHosting for configuration details)")

    if(config.ip.contains("\$ip"))
        config.ip = config.ip.replace("\$ip", getIP()).replace("\$port", "${config.port}")

    if(config.spotifyClient.isPresent && config.spotifySecret.isPresent && config.spotifyBase64.isEmpty) {
        config.spotifyBase64 = Base64.getEncoder().encodeToString("${config.spotifyClient()}:${config.spotifySecret()}".toByteArray(Charsets.UTF_8)).asOptional()
        config.spotifyClient = Optional.empty()
        config.spotifySecret = Optional.empty()
    }

    if(config.spotifyBase64.isEmpty)
        println("Note: Spotify Authentication details are absent; server running in offline/cache mode. New song retrievals will fail (see $projectHosting for more information)!")

    val vertx = Vertx.vertx()
    val options = HttpServerOptions()

    val http = vertx.createHttpServer(options)

    val router = Router.router(vertx)

    if(config.cors)
        router.route().handler(CorsHandler.create("*"))

    if(config.logAllPaths)
        router.route().handler { context ->
            println("Request was sent from ${context.request().remoteAddress()}: ${context.request().path()}")
            context.next()
        }

    router.route().handler { context ->
        if(config.redirects.containsKey(context.request().path()))
            context.reroute(config.redirects[context.request().path()])
        else
            context.next()
    }
    config.searchEndpoint.ifPresent { endpoint -> router.route(endpoint).blockingHandler(::search) }
    config.idEndpoint.ifPresent { endpoint -> router.route(endpoint).blockingHandler(::id) }
    config.audioEndpoint.ifPresent { endpoint -> router.route(endpoint).blockingHandler(::audio) }
    config.songEndpoint.ifPresent { endpoint -> router.route(endpoint).blockingHandler(::song) }
    config.fileManager.ifPresent { files -> router.route(files.first).handler(StaticHandler.create(files.second)) }
    config.indexEndpoint.ifPresent { endpoint -> router.route(endpoint).handler { context -> context.response().sendFile("index.html") } }
    config.faqEndpoint.ifPresent { endpoint -> router.route(endpoint).handler { context -> context.response().sendFile("faq.html") } }
    config.popularTracksEndpoint.ifPresent { endpoint -> router.route(endpoint).blockingHandler(::popular) }
    config.faviconPath.ifPresent { favicon -> router.route("/favicon.ico").handler(FaviconHandler.create(favicon)) }

    if(config.logMissingPaths) {
        router.route().handler { context ->
            println("Request was sent from ${context.request().remoteAddress()} that didn't match any paths, sending a 404 for ${context.request().path()}")
            context.response().setStatusCode(404).end()
        }
    }

    http.requestHandler { router.accept(it) }.listen(config.port)
    println("Listening at ${config.ip}")
}

var bearer = ""
var expires: Instant = Instant.now()

/** NYI */
fun popular(context: RoutingContext) {
    val request = context.request()
}
fun search(context: RoutingContext) {
    val request = context.request()

    if(!config.spotifyBase64.isPresent) {
        context.response().setStatusCode(501).putHeader("Content-Type", "application/json").end("{\"error\":\"Server not configured for new Spotify requests; bug the administrator\"}")
        return
    }

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
                track.put("url", "${config.ip}/song?id=${obj["id"]}")

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
        if(!config.spotifyBase64.isPresent) {
            context.response().setStatusCode(501).putHeader("Content-Type", "application/json").end("{\"error\":\"Server not configured for new Spotify requests; bug the administrator\"}")
            return
        }

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
                info.put("url", "${config.ip}/song?id=$id")
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
    config.spotifyBase64.ifPresent { base64 ->
        val token = Unirest
                .post("https://accounts.spotify.com/api/token")
                .header("Authorization", "Basic $base64")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("grant_type=client_credentials")
                .asJson()
                .body.`object`
        bearer = token["access_token"] as String
        expires = Instant.ofEpochMilli(System.currentTimeMillis() + (token["expires_in"] as Int).times(1000))
    }
}