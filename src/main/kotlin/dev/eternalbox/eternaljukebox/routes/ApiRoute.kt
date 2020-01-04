package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.handler.BodyHandler
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class ApiRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    init {
        apiRouter.post()
            .order(-1_000_000)
            .handler(
                BodyHandler.create()
                    .setBodyLimit(10_000_000)
                    .setDeleteUploadedFilesOnEnd(true)
            )
        apiRouter.route().last().suspendHandler(this::apiRouteNotFound)

        baseRouter.mountSubRouter("/api", apiRouter)
    }
}