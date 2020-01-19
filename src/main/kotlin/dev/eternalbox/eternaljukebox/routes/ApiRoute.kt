package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class ApiRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    init {
        apiRouter.route().last().suspendHandler(this::apiRouteNotFound)

        baseRouter.mountSubRouter("/api", apiRouter)
    }
}