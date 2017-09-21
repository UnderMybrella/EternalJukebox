package org.abimon.eternalJukebox

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.analysis.SpotifyAnalyser
import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.audio.YoutubeAudioSource
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.eternalJukebox.data.storage.LocalStorage
import org.abimon.eternalJukebox.handlers.OpenGraphHandler
import org.abimon.eternalJukebox.handlers.StaticResources
import org.abimon.eternalJukebox.handlers.api.AnalysisAPI
import org.abimon.eternalJukebox.handlers.api.AudioAPI
import org.abimon.eternalJukebox.handlers.api.IAPI
import org.abimon.eternalJukebox.handlers.api.SiteAPI
import org.abimon.eternalJukebox.objects.ConstantValues
import org.abimon.eternalJukebox.objects.EmptyDataAPI
import org.abimon.eternalJukebox.objects.JukeboxConfig
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.concurrent.scheduleAtFixedRate

object EternalJukebox {
    val jsonMapper: ObjectMapper = ObjectMapper()
            .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

    val yamlMapper: ObjectMapper = ObjectMapper(YAMLFactory())
            .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

    val jsonConfig: File = File("config.json")
    val yamlConfig: File = File("config.yaml")

    val secureRandom: SecureRandom = SecureRandom()

    val config: JukeboxConfig
    val vertx: Vertx
    val webserver: HttpServer

    val storage: IStorage
    val audio: IAudioSource

    val spotify: IAnalyser

    val requests: AtomicInteger = AtomicInteger(0)
    val hourlyRequests: AtomicInteger = AtomicInteger(0)

    val uniqueVisitors: AtomicInteger = AtomicInteger(0)
    val hourlyUniqueVisitors: AtomicInteger = AtomicInteger(0)

    val visitorAlgorithm: Algorithm
    val visitorVerifier: JWTVerifier

    val oldVisitorToken: String
        get() = JWT.create()
                .withIssuedAt(Date.from(Instant.EPOCH))
                .sign(visitorAlgorithm)
    val newVisitorToken: String
        get() = JWT.create()
                .withIssuedAt(Date.from(Instant.now()))
                .sign(visitorAlgorithm)

    val timer: Timer = Timer()
    val apis = ArrayList<IAPI>()

    val hourlyVisitorsAddress: ConcurrentSkipListSet<String> = ConcurrentSkipListSet()

    fun start() {
        webserver.listen(config.port)
        println("Now listening on port ${config.port}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        EternalJukebox.start()
    }

    init {
        if (jsonConfig.exists())
            config = jsonMapper.readValue(jsonConfig, JukeboxConfig::class.java)
        else if (yamlConfig.exists())
            config = yamlMapper.readValue(yamlConfig, JukeboxConfig::class.java)
        else
            config = JukeboxConfig()

        println("Loaded config: $config")

        // Config Handling

        visitorAlgorithm = Algorithm.HMAC512(ByteArray(config.visitorSecretSize).apply { secureRandom.nextBytes(this) })
        visitorVerifier = JWT.require(visitorAlgorithm).build()

        when (config.storageType.toUpperCase()) {
            "LOCAL" -> storage = LocalStorage
            else -> storage = LocalStorage
        }

        vertx = Vertx.vertx()
        webserver = vertx.createHttpServer()

        val mainRouter = Router.router(vertx)

        //Something something check for cookies
        mainRouter.route().handler(CookieHandler.create())
        mainRouter.route().handler {
            val ip = it.request().remoteAddress().host()

//            if(ip !in uniqueVisitorsAddress) {
//                it.data()[ConstantValues.UNIQUE_VISITOR]
//                uniqueVisitorsAddress.add(ip)
//            }

            if(ip !in hourlyVisitorsAddress) {
                it.data()[ConstantValues.HOURLY_UNIQUE_VISITOR]
                hourlyVisitorsAddress.add(ip)
            }

            it.next()
        }

        val runSiteAPI = config.disable["siteAPI"] != true

        if (runSiteAPI) {
            mainRouter.route().handler {
                requests.incrementAndGet()
                hourlyRequests.incrementAndGet()

                if(it[ConstantValues.HOURLY_UNIQUE_VISITOR, false, Boolean::class]) {
                    uniqueVisitors.incrementAndGet()
                    log("Unique visitor")
                }
                if(it[ConstantValues.HOURLY_UNIQUE_VISITOR, false, Boolean::class]) {
                    hourlyUniqueVisitors.incrementAndGet()
                    log("Unique hourly visitor")
                }

                it.next()
            }
        }

        val apiRouter = Router.router(vertx)

        if (config.disable["analysisAPI"] != true) {
            apis.add(AnalysisAPI)
            spotify = SpotifyAnalyser
        }
        else
            spotify = EmptyDataAPI

        if (config.disable["siteAPI"] != true)
            apis.add(SiteAPI)

        if (config.disable["audioAPI"] != true) {
            apis.add(AudioAPI)

            when (config.audioSourceType.toUpperCase()) {
                "YOUTUBE" -> audio = YoutubeAudioSource
                else -> audio = YoutubeAudioSource
            }
        } else {
            audio = EmptyDataAPI
        }

        apis.forEach { api ->
            val sub = Router.router(vertx)
            api.setup(sub)
            apiRouter.mountSubRouter(api.mountPath, sub)
        }
        mainRouter.mountSubRouter("/api", apiRouter)

        if (config.disable["openGraph"] != true)
            OpenGraphHandler.setup(mainRouter)
        if (config.disable["staticResources"] != true)
            StaticResources.setup(mainRouter)

        webserver.requestHandler(mainRouter::accept)

        if(config.disable["siteAPI"] != true)
            timer.scheduleAtFixedRate(0, 1000 * 60 * 60) { hourlyRequests.set(0); hourlyUniqueVisitors.set(0); hourlyVisitorsAddress.clear() }
        else
            timer.scheduleAtFixedRate(0, 1000 * 60 * 60) { hourlyVisitorsAddress.clear() }
    }
}