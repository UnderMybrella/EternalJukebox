package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class AnalysisRoute(jukebox: EternalJukebox): EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT   = "/analysis"
        private const val ANALYSIS_PATH = "/:service/:id"
        private const val UPLOAD_ANALYSIS_PATH = "/upload"
    }

    val router: Router = Router.router(vertx)

    suspend fun getAnalysis(context: RoutingContext) = apiNotImplemented(context)
    suspend fun retrieveAnalysis(context: RoutingContext) = apiNotImplemented(context)
    suspend fun uploadAnalysis(context: RoutingContext) = apiNotImplemented(context)

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(ANALYSIS_PATH).suspendHandler(this::getAnalysis)
        router.put(ANALYSIS_PATH).suspendHandler(this::retrieveAnalysis)
        router.post(UPLOAD_ANALYSIS_PATH).suspendHandler(this::uploadAnalysis)

        router.route(ANALYSIS_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(UPLOAD_ANALYSIS_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}