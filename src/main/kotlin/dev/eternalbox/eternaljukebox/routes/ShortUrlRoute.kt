package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class ShortUrlRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT = "/quark"

        private const val SHRINK_PATH = "/shrink"
        private const val GET_INFO_PATH = "/:id/info"
        private const val REDIRECT_PATH = "/:id/redirect"

        private const val ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_" //Base64 alphabet for now
    }

    val router = Router.router(vertx)

    suspend fun newShortUrl(): String = buildString { repeat(8) { append(ALPHABET.random()) } }

    suspend fun shrinkUrl(context: RoutingContext) = apiNotImplemented(context)
    suspend fun getUrlInfo(context: RoutingContext) = apiNotImplemented(context)
    suspend fun redirectToUrl(context: RoutingContext) = apiNotImplemented(context)

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.post(SHRINK_PATH).suspendHandler(this::shrinkUrl)
        router.get(GET_INFO_PATH).suspendHandler(this::getUrlInfo)
        router.get(REDIRECT_PATH).suspendHandler(this::redirectToUrl)
        router.head(REDIRECT_PATH).suspendHandler(this::redirectToUrl)

        router.route(SHRINK_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(GET_INFO_PATH).last().suspendHandler(this::apiMethodNotAllowedRouteSupportsGet)
        router.route(REDIRECT_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}