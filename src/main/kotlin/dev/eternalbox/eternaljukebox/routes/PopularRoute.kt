package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class PopularRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    class Factory : EternalboxRoute.Factory<PopularRoute>("PopularRoute") {
        override fun build(jukebox: EternalJukebox): PopularRoute = PopularRoute(jukebox)
    }

    companion object {
        private const val MOUNT_POINT = "/popular"

        private const val LIST_POPULAR_PATH = "/:type/list"
        private const val VISIT_SONG_PATH = "/:type/:service/:id"
    }

    val router = Router.router(vertx)

    suspend fun listPopular(context: RoutingContext) = apiNotImplemented(context)
    suspend fun visitSong(context: RoutingContext) = apiNotImplemented(context)

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(LIST_POPULAR_PATH).suspendHandler(this::listPopular)
        router.post(VISIT_SONG_PATH).suspendHandler(this::visitSong)

        router.route(LIST_POPULAR_PATH).last().suspendHandler(this::apiMethodNotAllowedRouteSupportsGet)
        router.route(VISIT_SONG_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}