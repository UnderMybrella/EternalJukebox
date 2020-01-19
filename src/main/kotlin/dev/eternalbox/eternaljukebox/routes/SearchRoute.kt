package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.data.EternalBoxSearchResult
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import dev.eternalbox.eternaljukebox.data.nullableResult
import dev.eternalbox.eternaljukebox.endSerialisedJsonAwait
import dev.eternalbox.eternaljukebox.routeWith
import dev.eternalbox.ytmusicapi.YoutubeMusicApi
import dev.eternalbox.ytmusicapi.getSongs
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
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
                "ytm" -> {
                    when (val response = ytmApi.search(query)) {
                        is JukeboxResult.Success -> {
                            response()
                                .endSerialisedJsonAwait(response.result.getSongs().mapNotNull { song ->
                                    jukebox.youtubeApi.videoFor(song.songID)
                                        .flatMapAwait { video ->
                                            jukebox.spotifyApi.searchTracks(buildString {
                                                append('"')
                                                append(video.title)
                                                append('"')

                                                if (song.album != null) {
                                                    append(" ")
                                                    append("album:")
                                                    append(song.album.albumName)
                                                }

                                                if (song.artist != null) {
                                                    append(" ")
                                                    append("artist:")
                                                    append(song.artist.artistName)
                                                }
                                            })
                                        }.nullableResult?.toList()
                                }.flatten().map(::EternalBoxSearchResult).distinctBy(EternalBoxSearchResult::analysisID))
                        }
                    }
                }
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