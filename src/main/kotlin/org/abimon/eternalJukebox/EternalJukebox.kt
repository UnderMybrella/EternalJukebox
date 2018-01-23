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
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CookieHandler
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.analysis.SpotifyAnalyser
import org.abimon.eternalJukebox.data.analytics.IAnalyticsProvider
import org.abimon.eternalJukebox.data.analytics.IAnalyticsStorage
import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.database.H2Database
import org.abimon.eternalJukebox.data.database.IDatabase
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.eternalJukebox.handlers.OpenGraphHandler
import org.abimon.eternalJukebox.handlers.StaticResources
import org.abimon.eternalJukebox.handlers.api.*
import org.abimon.eternalJukebox.objects.ConstantValues
import org.abimon.eternalJukebox.objects.EmptyDataAPI
import org.abimon.eternalJukebox.objects.JukeboxConfig
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.collections.ArrayList

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

    val BASE_64_URL = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_')

    val secureRandom: SecureRandom = SecureRandom()

    val config: JukeboxConfig
    val vertx: Vertx
    val webserver: HttpServer

    val storage: IStorage
    val audio: IAudioSource

    val spotify: IAnalyser

    val analytics: IAnalyticsStorage
    val analyticsProviders: List<IAnalyticsProvider>

    val database: IDatabase

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

    val schedule: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    val apis = ArrayList<IAPI>()

    val hourlyVisitorsAddress: ConcurrentSkipListSet<String> = ConcurrentSkipListSet()

    fun start() {
        webserver.listen(config.port)
        log("Now listening on port ${config.port}")
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

        log("Loaded config: $config")

        // Config Handling

        visitorAlgorithm = Algorithm.HMAC512(ByteArray(config.visitorSecretSize).apply { secureRandom.nextBytes(this) })
        visitorVerifier = JWT.require(visitorAlgorithm).build()

        storage = config.storageType.storage

        vertx = Vertx.vertx(VertxOptions().setMaxWorkerExecuteTime(90000000000))
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

            if (ip !in hourlyVisitorsAddress) {
                it.data()[ConstantValues.HOURLY_UNIQUE_VISITOR] = true
                hourlyVisitorsAddress.add(ip)
            }

            it.data()[ConstantValues.USER_UID] = UUID.randomUUID().toString()

            it.next()
        }

        val runSiteAPI = isEnabled("siteAPI")

        if (runSiteAPI) {
            mainRouter.route().handler {
                //                requests.incrementAndGet()
//                hourlyRequests.incrementAndGet()
//
//                if(it[ConstantValues.HOURLY_UNIQUE_VISITOR, false]) {
//                    uniqueVisitors.incrementAndGet()
//                    log("Unique visitor")
//                }
//                if(it[ConstantValues.HOURLY_UNIQUE_VISITOR, false]) {
//                    hourlyUniqueVisitors.incrementAndGet()
//                    log("Unique hourly visitor")
//                }

                it.next()
            }
        }

        val apiRouter = Router.router(vertx)

        if (isEnabled("analysisAPI"))
            apis.add(AnalysisAPI)

        if (runSiteAPI) {
            apis.add(SiteAPI)

            if(isEnabled("analytics")) {
                analytics = config.analyticsStorageType.analytics
                analyticsProviders = config.analyticsProviders.map { (type) -> type.provider }
            } else {
                analytics = EmptyDataAPI
                analyticsProviders = emptyList()
            }
        } else {
            analytics = EmptyDataAPI
            analyticsProviders = emptyList()
        }

        database = H2Database

        if (isEnabled("audioAPI")) {
            apis.add(AudioAPI)

            audio = config.audioSourceType.audio.objectInstance!!
        } else {
            audio = EmptyDataAPI
        }

        if (isEnabled("audioAPI") || isEnabled("analysisAPI"))
            spotify = SpotifyAnalyser
        else
            spotify = EmptyDataAPI

        if (isEnabled("nodeAPI"))
            apis.add(NodeAPI)

        analyticsProviders.forEach { provider -> provider.setupWebAnalytics(mainRouter) }

        apis.forEach { api ->
            val sub = Router.router(vertx)
            api.setup(sub)
            apiRouter.mountSubRouter(api.mountPath, sub)
        }
        mainRouter.mountSubRouter("/api", apiRouter)

        if (isEnabled("openGraph"))
            OpenGraphHandler.setup(mainRouter)
        if (isEnabled("staticResources"))
            StaticResources.setup(mainRouter)

        webserver.requestHandler(mainRouter::accept)

//        if(runSiteAPI)
//            schedule.scheduleAtFixedRate(0, 1000 * 60 * 60) { hourlyRequests.set(0); hourlyUniqueVisitors.set(0); hourlyVisitorsAddress.clear() }
//        else
//            schedule.scheduleAtFixedRate(0, 1000 * 60 * 60) { hourlyVisitorsAddress.clear() }
    }

    fun isEnabled(function: String): Boolean = config.disable[function] != true
}