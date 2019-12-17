package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import dev.eternalbox.eternaljukebox.routes.*
import dev.eternalbox.eternaljukebox.stores.DataStore
import dev.eternalbox.eternaljukebox.stores.LocalDataStore
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import java.io.File
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class EternalJukebox(val config: JsonNode) {
    val vertx = Vertx.vertx()
    val httpServer = vertx.createHttpServer()
    val baseRouter = Router.router(vertx)
    val apiRouter = Router.router(vertx)

    val apiRoute = ApiRoute(this)
    val metaRoute = MetaRoute(this)
    val analysisRoute = AnalysisRoute(this)
    val audioRoute = AudioRoute(this)
    val searchRoute = SearchRoute(this)
    val shortUrlRoute = ShortUrlRoute(this)
    val popular = PopularRoute(this)

    val analysisDataStore: DataStore = LocalDataStore(File("static_data"))

    val languageData: LanguageData = LanguageData(this)

    operator fun get(key: String): JsonNode? =
        System.getenv("JUKEBOX_${key.toUpperCase()}")?.let(::TextNode)
            ?: System.getProperty("jukebox.$key")?.let(::TextNode)
            ?: config[key]

    init {
        println("Base Router: ${System.identityHashCode(baseRouter)}")
        println("API  Router: ${System.identityHashCode(apiRouter)}")

        baseRouter.route("/static/*").handler(StaticHandler.create("static_data"))

        httpServer.requestHandler(baseRouter)
        httpServer.listen(9090)
    }
}