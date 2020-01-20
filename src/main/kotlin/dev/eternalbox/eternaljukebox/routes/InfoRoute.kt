package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.*
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.providers.info.InfoProvider
import dev.eternalbox.eternaljukebox.providers.info.SpotifyInfoProvider
import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.http.endAwait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class InfoRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    class Factory : EternalboxRoute.Factory<InfoRoute>("InfoRoute") {
        override fun build(jukebox: EternalJukebox): InfoRoute = InfoRoute(jukebox)
    }

    companion object {
        private const val MOUNT_POINT = "/info"
        private const val INFO_PATH = "/:service/:id"
        private const val UPLOAD_INFO_PATH = "/upload"
    }

    val router: Router = Router.router(vertx)
    val providers: Array<InfoProvider> = arrayOf(SpotifyInfoProvider(jukebox))

    suspend fun getInfo(context: RoutingContext) {
        routeWith(context) {
            val rawService = pathParam("service")
            val service = EnumAnalysisService.values().firstOrNull { service -> service.name.equals(rawService, true) }
            if (service == null) {
                response()
                    .setStatusCode(HttpResponseCodes.BAD_REQUEST)
                    .endJsonAwait {
                        "error" .. WebApiResponseCodes.INVALID_ANALYSIS_SERVICE
                        "message" .. errorMessage(
                            WebApiResponseMessages.INVALID_ANALYSIS_SERVICE,
                            rawService,
                            EnumAnalysisService.values().joinToString()
                        )
                    }
            } else {
                val songID = pathParam("id")

                when (val result = jukebox.infoDataStore.getTrackInfo(service, songID)) {
                    is JukeboxResult.Success -> response().endAwait(result.result, coroutineScope)
                    is JukeboxResult.Failure -> response().endAwait(result)
                }
            }
        }
    }

    suspend fun retrieveInfo(context: RoutingContext) {
        routeWith(context) {
            val rawService = pathParam("service")
            val service = EnumAnalysisService.values().firstOrNull { service -> service.name.equals(rawService, true) }
            if (service == null) {
                response()
                    .setStatusCode(HttpResponseCodes.BAD_REQUEST)
                    .endJsonAwait {
                        "error" .. WebApiResponseCodes.INVALID_ANALYSIS_SERVICE
                        "message" .. errorMessage(
                            WebApiResponseMessages.INVALID_ANALYSIS_SERVICE,
                            rawService,
                            EnumAnalysisService.values().joinToString()
                        )
                    }
            } else {
                val songID = pathParam("id")

                val errors: MutableList<JukeboxResult.Failure<DataResponse>> = ArrayList()
                for (provider in jukebox.infoProviders) {
                    if (provider.supportsTrackInfo(service)) {
                        val result = provider.retrieveTrackInfoFor(service, songID)
                        if (result is JukeboxResult.Success) {
                            when (val data = result.result) {
                                is DataResponse.ExternalUrl ->
                                    response()
                                        .putHeader("Location", data.url)
                                        .setStatusCode(data.redirectCode)
                                        .endAwait()

                                is DataResponse.DataSource ->
                                    response()
                                        .putHeader(HttpHeaderNames.CONTENT_LENGTH, data.size.toString())
                                        .putHeader(HttpHeaderNames.CONTENT_TYPE, data.contentType)
                                        .endAwait(data.source().asVertxChannel(coroutineScope, Dispatchers.IO))

                                is DataResponse.Data ->
                                    response()
                                        .putHeader(HttpHeaderNames.CONTENT_TYPE, data.contentType)
                                        .endAwait(Buffer.buffer(data.data))
                            }
                            return@routeWith
                        } else if (result is JukeboxResult.Failure) {
                            errors.add(result)
                        }

                        continue
                    } else {
                        continue
                    }
                }

                response()
                    .setStatusCode(400)
                    .endJsonAwait {
                        "errors" .. errors.map { failure ->
                            when (failure) {
                                is JukeboxResult.KnownFailure<*, *> -> json {
                                    "code" .. failure.errorCode
                                    "message" .. failure.errorMessage
                                    if (failure.additionalInfo != null)
                                        "additional" .. failure.additionalInfo
                                }
                                else -> json {
                                    "code" .. 0
                                    "message" .. "An unknown error occurred"
                                }
                            }
                        }
                    }
            }
        }
    }

    suspend fun uploadInfo(context: RoutingContext) {
        routeWith(context) {
            return apiNotImplemented(context)
        }
    }

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(INFO_PATH).suspendHandler(this::getInfo)
        router.put(INFO_PATH).suspendHandler(this::retrieveInfo)
        router.post(UPLOAD_INFO_PATH).suspendHandler(this::uploadInfo)

        router.route(INFO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(UPLOAD_INFO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}