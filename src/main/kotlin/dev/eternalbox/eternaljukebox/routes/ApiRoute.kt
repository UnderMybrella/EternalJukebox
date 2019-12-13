package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class ApiRoute(jukebox: EternalJukebox): EternalboxRoute(jukebox) {
    init {
        apiRouter.route().last().suspendHandler(this::apiRouteNotFound)

        baseRouter.mountSubRouter("/api", apiRouter)
    }
}