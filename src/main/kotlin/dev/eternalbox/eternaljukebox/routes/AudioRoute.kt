package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class AudioRoute(jukebox: EternalJukebox): EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT   = "/audio"
        private const val AUDIO_PATH = "/:service/:id"
        private const val UPLOAD_AUDIO_PATH = "/upload"
    }

    val router: Router = Router.router(vertx)

    suspend fun getAudio(context: RoutingContext) = apiNotImplemented(context)
    suspend fun retrieveAudio(context: RoutingContext) = apiNotImplemented(context)
    suspend fun uploadAudio(context: RoutingContext) = apiNotImplemented(context)

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(AUDIO_PATH).suspendHandler(this::getAudio)
        router.put(AUDIO_PATH).suspendHandler(this::retrieveAudio)
        router.post(UPLOAD_AUDIO_PATH).suspendHandler(this::uploadAudio)

        router.route(AUDIO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(UPLOAD_AUDIO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}