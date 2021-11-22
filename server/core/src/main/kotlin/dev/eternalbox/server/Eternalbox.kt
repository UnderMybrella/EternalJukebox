package dev.eternalbox.server

import dev.eternalbox.analysis.AnalysisApi
import dev.eternalbox.audio.AudioApi
import dev.eternalbox.audio.EnumAudioType
import dev.eternalbox.common.utils.getString
import dev.eternalbox.storage.base.EternalData
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

class EternalBox(application: Application) {
    val appConfig = application
        .environment
        .config
        .config("eternalbox")

    val configs = File(
        appConfig.propertyOrNull("config")?.getString()
        ?: System.getProperty("eternalbox.config")
        ?: System.getenv("ETERNALBOX_CONFIG")
        ?: "application.json"
    ).takeIf(File::exists)
        ?.readText()
        ?.let(Json::parseToJsonElement) as? JsonArray

    val analysisApis = configs?.mapNotNull { element ->
        val config = element as? JsonObject ?: return@mapNotNull null
        if (!config.getString("type").equals("analysis", true)) return@mapNotNull null

        Class.forName(config.getString("class"))
            .getConstructor(JsonObject::class.java)
            .newInstance(config) as AnalysisApi
    }?.associateBy { it.service.lowercase() } ?: emptyMap()

    val audioApis = configs?.mapNotNull { element ->
        val config = element as? JsonObject ?: return@mapNotNull null
        if (!config.getString("type").equals("audio", true)) return@mapNotNull null

        Class.forName(config.getString("class"))
            .getConstructor(JsonObject::class.java)
            .newInstance(config) as AudioApi
    }?.associateBy { it.service.lowercase() } ?: emptyMap()

    val DEFAULT_AUDIO_TYPE = EnumAudioType.OPUS
    val DEFAULT_FALLBACK_AUDIO_TYPE = EnumAudioType.M4A

    fun Routing.setup() {
        route("/api") {
            get("/{analysis_service}/{track_id}/audio/{audio_service}") {
                val analysisService = call.parameters.getOrFail("analysis_service")
                val audioService = call.parameters.getOrFail("audio_service")
                val trackID = call.parameters.getOrFail("track_id")

                val analysisApi = analysisApis[analysisService.lowercase()]
                                  ?: return@get call.respond(
                                      HttpStatusCode.ServiceUnavailable,
                                      mapOf("error" to "No analysis api for $analysisService installed")
                                  )

                val audioApi = audioApis[audioService.lowercase()]
                               ?: return@get call.respond(
                                   HttpStatusCode.ServiceUnavailable,
                                   mapOf("error" to "No audio api for $audioService installed")
                               )

                val track = analysisApi.getTrackDetails(trackID)
                            ?: return@get call.respond(HttpStatusCode.NotFound)

                val supportsOpus = call.request.queryParameters["supports_opus"]?.toBooleanStrictOrNull()
                val audioType = when (supportsOpus) {
                    true -> EnumAudioType.OPUS
                    false -> DEFAULT_FALLBACK_AUDIO_TYPE
                    null -> DEFAULT_AUDIO_TYPE
                }

                val url = audioApi.getAudioUrl(track, audioType) ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No audio url for $track"))

                when (val data = audioApi.getAudio(url, audioType, track)) {
                    is EternalData.Raw -> call.respondBytes(data.data, ContentType.parse(data.contentType))
                    is EternalData.Uploaded -> call.respondRedirect(data.url, permanent = false)
                    null -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "No audio data for $url ($track)"))
                }
            }

            route("/{service}") {
                route("/{track_id}") {
                    get("/analysis") {
                        val service = call.parameters.getOrFail("service")
                        val trackID = call.parameters.getOrFail("track_id")

                        val api = analysisApis[service.lowercase()]
                        if (api == null) {
                            call.respond(
                                HttpStatusCode.ServiceUnavailable,
                                mapOf("error" to "No analysis api for $service installed")
                            )
                        } else {
                            api.getAnalysis(trackID)?.let {
                                call.respond(it)
                            } ?: call.respond(HttpStatusCode.NotFound)
                        }
                    }

                    get("/info") {
                        val service = call.parameters.getOrFail("service")
                        val trackID = call.parameters.getOrFail("track_id")

                        val api = analysisApis[service.lowercase()]
                        if (api == null) {
                            call.respond(
                                HttpStatusCode.ServiceUnavailable,
                                mapOf("error" to "No analysis api for $service installed")
                            )
                        } else {
                            api.getTrackDetails(trackID)?.let {
                                call.respond(it)
                            } ?: call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }
    }

    init {
        application.install(ContentNegotiation) {
            json()
        }

        application.install(CORS) {
            anyHost()
        }

        application.install(ConditionalHeaders)
        application.install(StatusPages)

        application.routing { setup() }
    }
}

var engine: NettyApplicationEngine? = null

fun main(args: Array<String>): Unit {
    val applicationEnvironment = commandLineEnvironment(args)
    engine = NettyApplicationEngine(applicationEnvironment) { loadConfiguration(applicationEnvironment.config) }
    engine?.addShutdownHook {
        engine?.stop(3, 5, TimeUnit.SECONDS)
    }

    engine?.start(true)
}

fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
    val deploymentConfig = config.config("ktor.deployment")
    loadCommonConfiguration(deploymentConfig)
    deploymentConfig.propertyOrNull("requestQueueLimit")?.getString()?.toInt()?.let {
        requestQueueLimit = it
    }
    deploymentConfig.propertyOrNull("shareWorkGroup")?.getString()?.toBoolean()?.let {
        shareWorkGroup = it
    }
    deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getString()?.toInt()?.let {
        responseWriteTimeoutSeconds = it
    }
}

fun Application.module() {
    EternalBox(this)
}