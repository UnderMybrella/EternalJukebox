package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.endAwait
import dev.eternalbox.eternaljukebox.endJsonAwait
import dev.eternalbox.eternaljukebox.providers.audio.AudioProvider
import dev.eternalbox.eternaljukebox.withContext
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class AudioRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT = "/audio"
        private const val AUDIO_PATH = "/:audio_service/:analysis_service/:id"
        private const val UPLOAD_AUDIO_PATH = "/upload"
    }

    val router: Router = Router.router(vertx)
    val providers: Array<AudioProvider> = arrayOf()

    suspend fun getAudio(context: RoutingContext) {
        withContext(context) {
            val rawAudioService = pathParam("audio_service")
            val audioService =
                EnumAudioService.values().firstOrNull { service -> service.name.equals(rawAudioService, true) }
            if (audioService == null) {
                response()
                    .setStatusCode(HttpResponseCodes.BAD_REQUEST)
                    .endJsonAwait {
                        "error" .. WebApiResponseCodes.INVALID_AUDIO_SERVICE
                        "message" .. errorMessage(
                            WebApiResponseMessages.INVALID_AUDIO_SERVICE,
                            rawAudioService,
                            EnumAudioService.values().joinToString { it.name.capitalize() }
                        )
                        "audio_services" .. EnumAudioService.values()
                    }
            } else {
                val rawAnalysisService = pathParam("analysis_service")
                val analysisService = EnumAnalysisService.values()
                    .firstOrNull { service -> service.name.equals(rawAnalysisService, true) }
                if (analysisService == null) {
                    response()
                        .setStatusCode(HttpResponseCodes.BAD_REQUEST)
                        .endJsonAwait {
                            "error" .. WebApiResponseCodes.INVALID_ANALYSIS_SERVICE
                            "message" .. errorMessage(
                                WebApiResponseMessages.INVALID_ANALYSIS_SERVICE,
                                rawAnalysisService,
                                EnumAnalysisService.values().joinToString { it.name.capitalize() }
                            )
                            "analysis_services" .. EnumAnalysisService.values()
                        }
                } else {
                    val songID = pathParam("id")

                    when (val result = jukebox.audioDataStore.getAudio(audioService, analysisService, songID)) {
                        is JukeboxResult.Success -> response().endAwait(result.result, coroutineScope)
                        is JukeboxResult.Failure -> response().endAwait(result)
                    }
                }
            }
        }
    }

    suspend fun retrieveAudio(context: RoutingContext) = apiNotImplemented(context)
    suspend fun uploadAudio(context: RoutingContext) = apiNotImplemented(context)

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(AUDIO_PATH).suspendHandler(this::getAudio)
        router.put(AUDIO_PATH).suspendHandler(this::retrieveAudio)
        router.post(UPLOAD_AUDIO_PATH).suspendHandler(this::uploadAudio)

        router.route(AUDIO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(UPLOAD_AUDIO_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}