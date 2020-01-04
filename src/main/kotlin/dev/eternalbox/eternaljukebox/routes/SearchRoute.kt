package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.routeWith
import dev.eternalbox.ytmusicapi.YoutubeMusicApi
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class SearchRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT = "/search"

        private const val SEARCH_PATH = "/:service"
    }

    val router = Router.router(vertx)
    val ytmApi = YoutubeMusicApi()

    suspend fun search(context: RoutingContext) {
        routeWith(context) {
            val service = pathParam("service")
            val query = withContext(Dispatchers.IO) { URLDecoder.decode(queryParam("q").first(), "UTF-8") }

            when (service.toLowerCase()) {
//                "ytm" -> {
//                    val (_, response, search) = ytmApi.search(query)
//                    if (response.statusCode == 200) {
//                        response()
//                            .setStatusCode(HttpResponseCodes.OK)
//                            .endJsonAwait {
//                                "songs" .. search.getSongs()
//                                "video_urls" .. search.getSongs().map { (id) -> "https://youtu.be/$id" }
//                            }
//                    }
//                }
                else -> apiNotImplemented(context)
            }
        }
    }

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.post(SEARCH_PATH).suspendHandler(this::search)

        router.route(SEARCH_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}