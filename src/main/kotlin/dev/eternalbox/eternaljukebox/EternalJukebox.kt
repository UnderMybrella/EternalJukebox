package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import dev.eternalbox.eternaljukebox.apis.SpotifyApi
import dev.eternalbox.eternaljukebox.apis.YoutubeApi
import dev.eternalbox.eternaljukebox.providers.analysis.AnalysisProvider
import dev.eternalbox.eternaljukebox.providers.analysis.AnalysisProviderFactory
import dev.eternalbox.eternaljukebox.providers.audio.AudioProvider
import dev.eternalbox.eternaljukebox.providers.audio.AudioProviderFactory
import dev.eternalbox.eternaljukebox.providers.info.InfoProvider
import dev.eternalbox.eternaljukebox.providers.info.InfoProviderFactory
import dev.eternalbox.eternaljukebox.routes.*
import dev.eternalbox.eternaljukebox.stores.*
import dev.eternalbox.ytmusicapi.YoutubeMusicApi
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
@ExperimentalCoroutinesApi
class EternalJukebox(val config: JsonNode) {
    val vertx = Vertx.vertx()
    val httpServer = vertx.createHttpServer()
    val baseRouter = Router.router(vertx)
    val apiRouter = Router.router(vertx)

    val host: String by lazy { this["host"]?.asText() ?: "http://localhost:${httpServer.actualPort()}" }

    val sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx))

    val patreonRoute = PatreonRoute(this)

    val apiRoute = ApiRoute(this)
    val metaRoute = MetaRoute(this)
    val analysisRoute = AnalysisRoute(this)
    val audioRoute = AudioRoute(this)
    val searchRoute = SearchRoute(this)
    val shortUrlRoute = ShortUrlRoute(this)
    val popularRoute = PopularRoute(this)
    val infoRoute = InfoRoute(this)

    val analysisProviderFactories get() = ServiceLoader.load(AnalysisProviderFactory::class.java).toList()
    val audioProviderFactories get() = ServiceLoader.load(AudioProviderFactory::class.java).toList()
    val infoProviderFactories get() = ServiceLoader.load(InfoProviderFactory::class.java).toList()

    val dataStoreFactories get() = ServiceLoader.load(DataStoreFactory::class.java).toList()
    val analysisDataStoreFactories: List<AnalysisDataStoreFactory<*>>
        get() = ServiceLoader.load(AnalysisDataStoreFactory::class.java)
            .plus(dataStoreFactories)
    val audioDataStoreFactories: List<AudioDataStoreFactory<*>>
        get() = ServiceLoader.load(AudioDataStoreFactory::class.java)
            .plus(dataStoreFactories)
    val infoDataStoreFactories: List<InfoDataStoreFactory<*>>
        get() = ServiceLoader.load(InfoDataStoreFactory::class.java)
            .plus(dataStoreFactories)

    val analysisProviderNames = requireNotNull(
        (config["analysis_providers"] as? ArrayNode)?.map(JsonNode::asText)
            ?: (config["analysis_provider"]?.asText()?.let(::listOf))
    )
    val analysisProviders: List<AnalysisProvider> = analysisProviderFactories
        .filter { factory -> factory.name in analysisProviderNames }
        .map { factory ->
            factory.configure(this)
            factory.build(this)
        }

    val audioProviderNames = requireNotNull(
        (config["audio_providers"] as? ArrayNode)?.map(JsonNode::asText)
            ?: (config["audio_provider"]?.asText()?.let(::listOf))
    )
    val audioProviders: List<AudioProvider> = audioProviderFactories
        .filter { factory -> factory.name in audioProviderNames }
        .map { factory ->
            factory.configure(this)
            factory.build(this)
        }

    val infoProviderNames = requireNotNull(
        (config["info_providers"] as? ArrayNode)?.map(JsonNode::asText)
            ?: (config["info_provider"]?.asText()?.let(::listOf))
    )
    val infoProviders: List<InfoProvider> = infoProviderFactories
        .filter { factory -> factory.name in infoProviderNames }
        .map { factory ->
            factory.configure(this)
            factory.build(this)
        }

    val analysisDataStoreName = requireNotNull(config["analysis_data_store"]).asText()
    val analysisDataStore: AnalysisDataStore = analysisDataStoreFactories
        .firstOrNull { factory -> factory.name == analysisDataStoreName }
        ?.let { factory ->
            factory.configure(this)
            factory.build(this)
        } ?: NoOpDataStore

    val audioDataStoreName = requireNotNull(config["audio_data_store"]).asText()
    val audioDataStore: AudioDataStore = audioDataStoreFactories
        .firstOrNull { factory -> factory.name == audioDataStoreName }
        ?.let { factory ->
            factory.configure(this)
            factory.build(this)
        } ?: NoOpDataStore

    val infoDataStoreName = requireNotNull(config["info_data_store"]).asText()
    val infoDataStore: InfoDataStore = infoDataStoreFactories
        .firstOrNull { factory -> factory.name == infoDataStoreName }
        ?.let { factory ->
            factory.configure(this)
            factory.build(this)
        } ?: NoOpDataStore

    val spotifyApi: SpotifyApi by lazy { SpotifyApi(this) }
    val youtubeApi: YoutubeApi by lazy { YoutubeApi(this) }
    val youtubeMusicApi: YoutubeMusicApi by lazy { YoutubeMusicApi(this) }

    val languageData: LanguageData = LanguageData(this)

    operator fun get(key: String): JsonNode? =
        System.getenv("JUKEBOX_${key.toUpperCase()}")?.let(::TextNode)
            ?: System.getProperty("jukebox.$key")?.let(::TextNode)
            ?: config[key]

    init {
        println("Base Router: ${System.identityHashCode(baseRouter)}")
        println("API  Router: ${System.identityHashCode(apiRouter)}")

        baseRouter.post()
            .order(Int.MIN_VALUE)
            .handler(
                BodyHandler.create()
                    .setBodyLimit(10_000_000)
                    .setDeleteUploadedFilesOnEnd(true)
            )

        baseRouter.route().order(Int.MIN_VALUE).handler(sessionHandler)
        baseRouter.route().order(Int.MIN_VALUE)
            .handler(CorsHandler.create("*").allowedMethods(setOf(HttpMethod.GET, HttpMethod.PUT, HttpMethod.POST)))

        baseRouter.route("/static/*").handler(StaticHandler.create("static_data"))

        httpServer.requestHandler(baseRouter)
        httpServer.listen(9090)
    }
}