package dev.eternalbox.eternaljukebox.routes

import com.fasterxml.jackson.core.JsonParseException
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.coroutines.awaitString
import dev.eternalbox.eternaljukebox.*
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.providers.analysis.AnalysisProvider
import dev.eternalbox.eternaljukebox.providers.analysis.SpotifyAnalysisProvider
import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.file.readFileAwait
import io.vertx.kotlin.core.http.endAwait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.InputStream
import java.security.MessageDigest
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class AnalysisRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    companion object {
        private const val MOUNT_POINT = "/analysis"
        private const val ANALYSIS_PATH = "/:service/:id"
        private const val UPLOAD_ANALYSIS_PATH = "/upload"
    }

    val router: Router = Router.router(vertx)
    val providers: Array<AnalysisProvider> = arrayOf(SpotifyAnalysisProvider(jukebox))

    @ExperimentalCoroutinesApi
    suspend fun getAnalysis(context: RoutingContext) {
        withContext(context) {
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

                for (provider in providers) {
                    if (provider.supportsAnalysis(service)) {
                        val result = provider.getAnalysisFor(service, songID) ?: continue
                        when (result) {
                            is DataResponse.ExternalUrl ->
                                response()
                                    .putHeader("Location", result.url)
                                    .setStatusCode(result.redirectCode)
                                    .endAwait()

                            is DataResponse.DataSource ->
                                response()
                                    .putHeader(HttpHeaderNames.CONTENT_LENGTH, result.size.toString())
                                    .putHeader(HttpHeaderNames.CONTENT_TYPE, result.contentType)
                                    .endAwait(result.source().asVertxChannel(coroutineScope, Dispatchers.IO))

                            is DataResponse.Data ->
                                response()
                                    .putHeader(HttpHeaderNames.CONTENT_TYPE, result.contentType)
                                    .endAwait(Buffer.buffer(result.data))
                        }

                        return@withContext
                    } else {
                        continue
                    }
                }

                response()
                    .setStatusCode(404)
                    .endJsonAwait {
                        "error" .. WebApiResponseCodes.NO_ANALYSIS_PROVIDER
                        "message" .. errorMessage(WebApiResponseMessages.NO_ANALYSIS_PROVIDER, service, songID)
                    }
            }
        }
    }

    suspend fun retrieveAnalysis(context: RoutingContext) {
        withContext(context) {
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
                for (provider in providers) {
                    if (provider.supportsAnalysis(service)) {
                        val result = provider.retrieveAnalysisFor(service, songID)
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
                            return@withContext
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

    suspend fun uploadAnalysis(context: RoutingContext) {
        withContext(context) {
            val analysisFileUpload =
                context.fileUploads().firstOrNull { upload -> upload.contentType() == "application/json" }
            val analysisString =
                if (analysisFileUpload != null) String(context.vertx().fileSystem().readFileAwait(analysisFileUpload.uploadedFileName()).bytes)
                else context.bodyAsString

            val analysisJson = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) { JSON_MAPPER.readTree(analysisString) }
            } catch (jsonException: JsonParseException) {
                return response()
                    .setStatusCode(400)
                    .endJsonAwait {
                        "error" .. WebApiResponseCodes.INVALID_UPLOADED_ANALYSIS
                        "message" .. errorMessage(WebApiResponseMessages.INVALID_UPLOADED_ANALYSIS)
                        "source" .. analysisString
                        "exception" .. jsonException
                    }
            }

            val result = AnalysisProvider.parseAnalysisData(analysisJson)
                .flatMapAwait { node ->
                    val analysisData =
                        kotlinx.coroutines.withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsBytes(node) }

                    val md = MessageDigest.getInstance("SHA-256")
                    val hashBytes = md.digest(analysisData)
                    val hash = bytesToHex(hashBytes)

                    //Replace this fuckery with a database call
                    if (jukebox.analysisDataStore.hasAnalysisStored(EnumAnalysisService.JSON, hash)) {
                        jukebox.analysisDataStore.getAnalysis(EnumAnalysisService.JSON, hash)
                            .flatMapAwait { response ->
                                when (response) {
                                    is DataResponse.ExternalUrl ->
                                        try {
                                            val properURL = if (response.url.startsWith("/"))
                                                request().absoluteURI().replace(request().uri(), response.url)
                                            else
                                                response.url
                                            JukeboxResult.Success(Fuel.get(properURL).awaitString())
                                        } catch (fuel: FuelError) {
                                            if (fuel.response.statusCode == -1)
                                                JukeboxResult.KnownFailure<String, Throwable>(
                                                    0,
                                                    fuel.message ?: "(no message)",
                                                    fuel.exception
                                                )
                                            else {
                                                JukeboxResult.KnownFailure<String, ByteArray>(
                                                    fuel.response.statusCode,
                                                    fuel.response.responseMessage,
                                                    fuel.response.data
                                                )
                                            }
                                        }
                                    is DataResponse.DataSource -> JukeboxResult.Success(
                                        String(
                                            response.source().use(InputStream::readBytes)
                                        )
                                    )
                                    is DataResponse.Data -> JukeboxResult.Success(String(response.data))
                                }
                            }
                    } else {
                        val newID = jukebox.shortUrlRoute.newShortUrl()
                        jukebox.analysisDataStore.storeAnalysis(
                            EnumAnalysisService.JSON,
                            newID,
                            analysisData
                        ).flatMapAwait {
                            jukebox.analysisDataStore.storeAnalysis(
                                EnumAnalysisService.JSON,
                                hash,
                                newID.toByteArray()
                            )
                        }.map { newID }
                    }
                }

            when (result) {
                is JukeboxResult.Success -> response()
                    .setStatusCode(HttpResponseCodes.CREATED)
                    .endJsonAwait {
                        "id" .. result.result
                    }
                is JukeboxResult.KnownFailure<*, *> -> response()
                    .setStatusCode(
                        when {
                            WebApiResponseCodes isHttpClientResponseCode result.errorCode -> WebApiResponseCodes asHttpResponseCode result.errorCode
                            WebApiResponseCodes isHttpServerResponseCode result.errorCode -> WebApiResponseCodes asHttpResponseCode result.errorCode
                            WebApiResponseCodes isJukeboxClientResponseCode result.errorCode -> 400
                            WebApiResponseCodes isJukeboxServerResponseCode result.errorCode -> 500
                            else -> 500
                        }
                    )
                    .endJsonAwait {
                        "error" .. result.errorCode
                        "message" .. result.errorMessage
                        if (result.additionalInfo != null)
                            "additional" .. result.additionalInfo
                    }
                is JukeboxResult.UnknownFailure -> response()
                    .setStatusCode(500)
                    .endJsonAwait {
                        "error" .. 0
                        "message" .. "An unknown error occurred"
                    }
            }
        }
    }

    init {
        apiRouter.mountSubRouter(MOUNT_POINT, router)

        router.get(ANALYSIS_PATH).suspendHandler(this::getAnalysis)
        router.put(ANALYSIS_PATH).suspendHandler(this::retrieveAnalysis)
        router.post(UPLOAD_ANALYSIS_PATH).suspendHandler(this::uploadAnalysis)

        router.route(ANALYSIS_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
        router.route(UPLOAD_ANALYSIS_PATH).last().suspendHandler(this::apiMethodNotAllowedForRoute)
    }
}