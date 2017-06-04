package org.abimon.eternalJukebox

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.mashape.unirest.http.JsonNode
import com.mashape.unirest.http.Unirest
import com.mashape.unirest.http.exceptions.UnirestException
import com.mashape.unirest.request.HttpRequestWithBody
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import org.abimon.eternalJukebox.objects.JukeboxConfig
import org.abimon.notifly.NotificationPayload
import org.abimon.notifly.notification
import org.abimon.visi.collections.PoolableObject
import org.abimon.visi.io.errPrintln
import org.abimon.visi.io.forceError
import org.abimon.visi.lang.asOptional
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import kotlin.reflect.KClass

val eternalDir = File("eternal")
val songsDir = File("songs")
val audioDir = File("audio")
val logDir = File("logs")
val tmpUploadDir = File("uploads")
val profileDir = File("profiles")

val configFile = File("config.json")
val projectHosting = "https://github.com/UnderMybrella/EternalJukebox" //Just in case this changes or needs to be referenced

var config = JukeboxConfig()

fun getIP(): String {
    try {
        Unirest.get("https://google.com").asString().body
    } catch(e: UnirestException) {
        println("The internet's offline. Or something *really* bad has happened: $e")
        return "localhost"
    }

    for (web in arrayOf("http://ipecho.net/plain", "http://icanhazip.com", "http://ipinfo.io/ip",
            "https://ident.me/", "http://checkip.amazonaws.com", "http://smart-ip.net/myip",
            "http://bot.whatismyipaddress.com", "https://secure.informaction.com/ipecho/", "http://curlmyip.com")) {
        try {
            return Unirest.get(web).asString().body
        } catch(e: UnirestException) {
            println("$web is offline, trying the next one...")
        }
    }

    return "localhost"
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

    when (response.status) {
        201 -> return (JSONObject(response.body)["html_url"] as String).asOptional()
        else -> {
            println("Gist failed with error ${response.status}: ${response.body}")
            return Optional.empty()
        }
    }
}

val objMapper: ObjectMapper = ObjectMapper()
        .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
val b64Encoder: Base64.Encoder = Base64.getUrlEncoder()

fun main(args: Array<String>) {
    Unirest.setObjectMapper(object : com.mashape.unirest.http.ObjectMapper {
        override fun writeValue(value: Any): String = objMapper.writeValueAsString(value)
        override fun <T : Any?> readValue(value: String, valueType: Class<T>): T = objMapper.readValue(value, valueType)
    })

    if (!eternalDir.exists())
        eternalDir.mkdir()
    if (!songsDir.exists())
        songsDir.mkdir()
    if (!audioDir.exists())
        audioDir.mkdir()
    if (!logDir.exists())
        logDir.mkdir()
    if (!tmpUploadDir.exists())
        tmpUploadDir.mkdir()
    if (!profileDir.exists())
        profileDir.mkdir()

    if (configFile.exists()) {
        try {
            config = objMapper.readValue(configFile, JukeboxConfig::class.java)
        } catch(jsonError: JSONException) {
            error("Something went wrong reading the config file ($configFile): $jsonError")
        }
    } else
        println("Config file ($configFile) does not exist, using default values (see $projectHosting for configuration details)")

    if (config.ip.contains("\$ip") || config.ip.contains("\$port"))
        config.ip = config.ip.replace("\$ip", getIP()).replace("\$port", "${config.port}")

    if (config.spotifyClient != null && config.spotifySecret != null && config.spotifyBase64 == null) {
        config.spotifyBase64 = b64Encoder.encodeToString("${config.spotifyClient}:${config.spotifySecret}".toByteArray(Charsets.UTF_8))
        config.spotifyClient = null
        config.spotifySecret = null
    }

    if (config.spotifyBase64 == null)
        println("Note: Spotify Authentication details are absent; server running in offline/cache mode. New song retrievals will fail (see $projectHosting for more information)!")

    if (!mysqlEnabled())
        println("Note: MySQL details are absent; server running in 'simple' mode. Popular song retrieval will fail, as will logins and a bunch of other things (see $projectHosting for more information)!")
    else {
        createAccountsTable()
        createPopularJukeboxTable()
        createPopularCanonizerTable()
        createShortURLTable()
    }

    if (isInsecure()) {
        if (config.httpsOverride)
            errPrintln("You've attempted to use secure features of this program, such as logins, while not using security features for them (HTTPS, Secure Cookies). See $projectHosting for more information.\nYou've then opted to override this using httpsOverride. This is not a good idea. I repeat, do *not* use this in a production environment without an exceptionally good reason!")
        else
            forceError("Error: You've attempted to use secure features of this program, such as logins, while not using security features for them (HTTPS, Secure Cookies). See $projectHosting for more information.\nIf you absolutely *must* override this (development environment), then you can override this in the config file with the key httpsOverride. Do not do this in a production environment, short of some other protocol to handle security (eg: CloudFlare)")
    }

    if (!config.cacheFiles)
        System.setProperty("vertx.disableFileCPResolving", "true")

    val vertx = Vertx.vertx(VertxOptions().setBlockedThreadCheckInterval(config.vertxBlockingTime))
    val options = HttpServerOptions()

    if(config.ssl != null) {
        options.pemKeyCertOptions = PemKeyCertOptions().setKeyPath(config.ssl!!.key).setCertPath(config.ssl!!.cert)
        options.isSsl = true
    }

    val http = vertx.createHttpServer(options)

    val router = Router.router(vertx)

    if (config.logAllPaths && mysqlEnabled()) {
        createRequestsTable()
        router.route().handler(::logRequest)
    }

    if (config.cors)
        router.route().handler(CorsHandler.create("*").allowCredentials(false).allowedMethod(HttpMethod.GET))

    StaticResources.setup(router)
    API.setup(vertx, router)

    if (config.logMissingPaths) {
        router.route().handler { context ->
            context.response().setStatusCode(404).end()
            println("Request was sent from ${context.request().remoteAddress()} that didn't match any paths, sending a 404 for ${context.request().path()}")
            sendFirebaseMessage(notification {
                title("[EternalJukebox] Unknown Path")
                body("${context.request().remoteAddress()} requested ${context.request().path()}, returning with 404")
            }.asOptional())
        }
    }

    http.requestHandler(router::accept).listen(config.port)
    println("Listening at ${config.ip}")
}

fun <T : Any> JsonObject.mapTo(clazz: KClass<T>): T = objMapper.readValue(toString(), clazz.java)

fun makeConnection(): PoolableObject<Connection> = PoolableObject(DriverManager.getConnection("jdbc:mysql://localhost/${config.mysqlDatabase}?user=${config.mysqlUsername}&password=${config.mysqlPassword}&serverTimezone=GMT"))

fun sendFirebaseMessage(notificationPayload: Optional<NotificationPayload> = Optional.empty(), dataPayload: Optional<JSONObject> = Optional.empty()) {
    if(config.firebaseApp != null) {
        if(config.firebaseDevice != null) {
            val payload = JSONObject()
            payload.put("to", config.firebaseDevice)
            notificationPayload.ifPresent { notification -> payload.put("notification", JSONObject(objMapper.writeValueAsString(notification))) }
            dataPayload.ifPresent { data -> payload.put("data", data) }
            Unirest.post("https://fcm.googleapis.com/fcm/send").header("Authorization", "key=${config.firebaseApp}").jsonBody(payload).asJson().body.toJsonObject()
        }
    }
}

fun mysqlEnabled(): Boolean = config.mysqlDatabase != null && config.mysqlUsername != null && config.mysqlPassword != null

fun <T : HttpRequestWithBody> T.jsonBody(json: JSONObject): T {
    header("Content-Type", "application/json")
    body(json.toString())
    return this
}
fun JsonNode.toJsonObject(): JSONObject = JSONObject(`object`.toString())
fun error(msg: Any) {
    errPrintln(msg)
    sendFirebaseMessage(notification {
        title("[EternalJukebox] Error")
        body(msg.toString())
    }.asOptional())
}

fun isInsecure(): Boolean = API.allowGoogleLogins() && (!config.secureCookies || config.ssl == null)