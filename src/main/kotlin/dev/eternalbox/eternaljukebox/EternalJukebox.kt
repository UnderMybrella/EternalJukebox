package dev.eternalbox.eternaljukebox

import dev.eternalbox.eternaljukebox.routes.*
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class EternalJukebox(val config: Unit) {
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

    val languageData: LanguageData = LanguageData(this)

    init {
        println("Base Router: ${System.identityHashCode(baseRouter)}")
        println("API  Router: ${System.identityHashCode(apiRouter)}")

        httpServer.requestHandler(baseRouter)
        httpServer.listen(9090)
    }
}