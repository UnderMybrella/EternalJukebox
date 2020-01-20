package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class ApiRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    class Factory : EternalboxRoute.Factory<ApiRoute>("ApiRoute") {
        override fun build(jukebox: EternalJukebox): ApiRoute = ApiRoute(jukebox)
    }

    init {
        apiRouter.route().last().suspendHandler(this::apiRouteNotFound)

        baseRouter.mountSubRouter("/api", apiRouter)
    }
}