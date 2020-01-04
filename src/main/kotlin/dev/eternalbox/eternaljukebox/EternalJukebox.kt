package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import dev.eternalbox.eternaljukebox.apis.SpotifyApi
import dev.eternalbox.eternaljukebox.providers.info.InfoProvider
import dev.eternalbox.eternaljukebox.providers.info.SpotifyInfoProvider
import dev.eternalbox.eternaljukebox.routes.*
import dev.eternalbox.eternaljukebox.stores.DataStore
import dev.eternalbox.eternaljukebox.stores.LocalDataStore
import dev.eternalbox.ytmusicapi.YoutubeMusicApi
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
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

    val analysisDataStore: DataStore
    val audioDataStore: DataStore
    val infoDataStore: DataStore

    val infoProvider: InfoProvider = SpotifyInfoProvider(this)

    val spotifyApi = SpotifyApi(this)
    val youtubeMusicApi = YoutubeMusicApi(this)

    val languageData: LanguageData = LanguageData(this)

    operator fun get(key: String): JsonNode? =
        System.getenv("JUKEBOX_${key.toUpperCase()}")?.let(::TextNode)
            ?: System.getProperty("jukebox.$key")?.let(::TextNode)
            ?: config[key]

    init {
        println("Base Router: ${System.identityHashCode(baseRouter)}")
        println("API  Router: ${System.identityHashCode(apiRouter)}")

        val localDataStore = LocalDataStore(File("static_data"))

        analysisDataStore = localDataStore
        audioDataStore = localDataStore
        infoDataStore = localDataStore

        baseRouter.route().order(Int.MIN_VALUE).handler(sessionHandler)

        baseRouter.route().order(Int.MIN_VALUE).handler(CorsHandler.create("*").allowedMethods(setOf(HttpMethod.GET, HttpMethod.PUT, HttpMethod.POST)))

        baseRouter.route("/static/*").handler(StaticHandler.create("static_data"))

        httpServer.requestHandler(baseRouter)
        httpServer.listen(9090)
    }
}