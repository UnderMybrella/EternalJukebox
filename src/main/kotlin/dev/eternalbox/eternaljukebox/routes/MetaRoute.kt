package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.endJsonAwait
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class MetaRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    class Factory : EternalboxRoute.Factory<MetaRoute>("MetaRoute") {
        override fun build(jukebox: EternalJukebox): MetaRoute = MetaRoute(jukebox)
    }

    companion object {
        private const val MOUNT_POINT = "/meta"
        private const val VERSION_PATH = "/version"

    }

    val router: Router = Router.router(vertx)

    suspend fun getVersion(context: RoutingContext) {
        context.response().endJsonAwait {
            "version" .. "1.0.0"
        }
    }

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(VERSION_PATH).suspendHandler(this::getVersion)
        router.route(VERSION_PATH).last().suspendHandler(this::apiMethodNotAllowedRouteSupportsGet)
    }
}