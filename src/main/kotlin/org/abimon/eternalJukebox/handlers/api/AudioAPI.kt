package org.abimon.eternalJukebox.handlers.api

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.data.audio.YoutubeAudioSource
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.visi.io.FileDataSource
import org.abimon.visi.security.md5Hash
import org.abimon.visi.security.sha512Hash
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

object AudioAPI : IAPI {
    override val mountPath: String = "/audio"
    override val name: String = "Audio"
    val logger = LoggerFactory.getLogger("AudioApi")

    val format: String
        get() = EternalJukebox.config.audioSourceOptions["AUDIO_FORMAT"] as? String ?: "m4a"
    val uuid: String
        get() = UUID.randomUUID().toString()

    val base64Encoder: Base64.Encoder by lazy { Base64.getUrlEncoder() }

    val mime: String
        get() = EternalJukebox.config.audioSourceOptions["AUDIO_MIME"] as? String ?: run {
            when (format) {
                "m4a" -> return@run "audio/m4a"
                "aac" -> return@run "audio/aac"
                "mp3" -> return@run "audio/mpeg"
                "ogg" -> return@run "audio/ogg"
                "wav" -> return@run "audio/wav"
                else -> return@run "audio/mpeg"
            }
        }

    override fun setup(router: Router) {
        router.get("/jukebox/:id").suspendingHandler(AudioAPI::jukeboxAudio)
        router.get("/jukebox/:id/location").suspendingHandler(AudioAPI::jukeboxLocation)
        router.get("/external").suspendingHandler(AudioAPI::externalAudio)
        router.post("/upload")
            .handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true).setBodyLimit(25 * 1000 * 1000))
        router.post("/upload").suspendingHandler(this::upload)
    }

    suspend fun jukeboxAudio(context: RoutingContext) {
        if (EternalJukebox.storage.shouldStore(EnumStorageType.AUDIO)) {
            val id = context.pathParam("id")

            val audioOverride = withContext(Dispatchers.IO) { EternalJukebox.database.provideAudioTrackOverride(id, context.clientInfo) }
            if (audioOverride != null)
                return context.response().redirect("/api/audio/external?url=${withContext(Dispatchers.IO) {
                    URLEncoder.encode(
                        audioOverride,
                        "UTF-8"
                    )
                }}")

            val update = context.request().getParam("update")?.toBoolean() ?: false
            if (EternalJukebox.storage.isStored("$id.$format", EnumStorageType.AUDIO) && !update) {
                if (EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO, context, context.clientInfo))
                    return

                val data = EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO, context.clientInfo)
                if (data != null)
                    return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(data, mime)
            }

            if (update)
                logger.trace(
                    "[{}] {} is requesting an update for {}",
                    context.clientInfo.userUID,
                    context.clientInfo.remoteAddress,
                    id
                )

            val track = EternalJukebox.spotify.getInfo(id, context.clientInfo) ?: run {
                logger.warn("[{}] No track info for {}; returning 400", context.clientInfo.userUID, id)
                return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Track info not found for $id",
                        "client_uid" to context.clientInfo.userUID
                    )
                )
            }

            val audio = EternalJukebox.audio?.provide(track, context.clientInfo)

            if (audio == null)
                context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Audio is null",
                        "client_uid" to context.clientInfo.userUID
                    )
                )
            else {
                if (EternalJukebox.storage.isStored("$id.$format", EnumStorageType.AUDIO) && !update) {
                    if (EternalJukebox.storage.provide(
                            "$id.$format",
                            EnumStorageType.AUDIO,
                            context,
                            context.clientInfo
                        )
                    )
                        return

                    val data = EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO, context.clientInfo)
                    if (data != null)
                        return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(data, mime)
                }

                return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(audio, mime)
            }
        } else {
            context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(501).end(
                jsonObjectOf(
                    "error" to "Configured storage method does not support storing AUDIO",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        }
    }

    suspend fun jukeboxLocation(context: RoutingContext) {
        val id = context.pathParam("id")

        val audioOverride = withContext(Dispatchers.IO) { EternalJukebox.database.provideAudioTrackOverride(id, context.clientInfo) }
        if (audioOverride != null)
            return context.endWithStatusCode(200) { if (!audioOverride.startsWith("upl")) this["url"] = audioOverride }

        val track = EternalJukebox.spotify.getInfo(id, context.clientInfo) ?: run {
            logger.warn("[{}] No track info for {}; returning 400", context.clientInfo.userUID, id)
            return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(
                jsonObjectOf(
                    "error" to "Track info not found for $id",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        }

        val url = EternalJukebox.audio?.provideLocation(track, context.clientInfo)

        context.endWithStatusCode(200) { if (url != null) this["url"] = url.toExternalForm() }
    }

    // url -> fallbackURL -> fallbackID
    suspend fun externalAudio(context: RoutingContext) {
        val url = context.request().getParam("url")

        if (url != null) {
            if (url.startsWith("upl:")) {
                if (!EternalJukebox.storage.shouldStore(EnumStorageType.UPLOADED_AUDIO)) {
                    logger.warn(
                        "[{}] Rerouting external audio request of URL {}; this server does not support uploaded audio",
                        context.clientInfo.userUID,
                        url
                    )
                    return context.reroute(
                        "/api" + mountPath + "/jukebox/${context.request().getParam("fallbackID")
                            ?: "7GhIk7Il098yCjg4BQjzvb"}"
                    )
                }

                val hash = url.substringAfter("upl:")
                if (EternalJukebox.storage.isStored("$hash.$format", EnumStorageType.UPLOADED_AUDIO)) {
                    if (!EternalJukebox.storage.provide(
                            "$hash.$format",
                            EnumStorageType.UPLOADED_AUDIO,
                            context,
                            context.clientInfo
                        )
                    ) {
                        val data = EternalJukebox.storage.provide(
                            "$hash.$format",
                            EnumStorageType.UPLOADED_AUDIO,
                            context.clientInfo
                        )
                        if (data != null)
                            return context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                                .end(data, AudioAPI.mime)
                    } else
                        return
                } else {
                    logger.warn(
                        "[{}] Rerouting external audio request of URL {}; no storage for {}.{}",
                        context.clientInfo.userUID,
                        url,
                        hash,
                        format
                    )
                    return context.reroute(
                        "/api" + mountPath + "/jukebox/${context.request().getParam("fallbackID")
                            ?: "7GhIk7Il098yCjg4BQjzvb"}"
                    )
                }
            } else {
                val (_, response) = Fuel.headOrGet(url)
                if (response.statusCode < 300) {
                    val mime = response.headers["Content-Type"].firstOrNull()

                    if (mime != null && mime.startsWith("audio"))
                        return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).redirect(url)
                    else {
                        if (EternalJukebox.storage.shouldStore(EnumStorageType.EXTERNAL_AUDIO)) {
                            val b64 = base64Encoder.encodeToString(url.toByteArray(Charsets.UTF_8)).md5Hash()

                            val update = context.request().getParam("update")?.toBoolean() ?: false
                            if (EternalJukebox.storage.isStored(
                                    "$b64.$format",
                                    EnumStorageType.EXTERNAL_AUDIO
                                ) && !update
                            ) {
                                if (EternalJukebox.storage.provide(
                                        "$b64.$format",
                                        EnumStorageType.EXTERNAL_AUDIO,
                                        context,
                                        context.clientInfo
                                    )
                                )
                                    return

                                val data = EternalJukebox.storage.provide(
                                    "$b64.$format",
                                    EnumStorageType.EXTERNAL_AUDIO,
                                    context.clientInfo
                                )
                                if (data != null)
                                    return context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                                        .end(data, AudioAPI.mime)
                            }

                            if (update)
                                logger.trace(
                                    "[{}] {} is requesting an update for {} / {}",
                                    context.clientInfo.userUID,
                                    context.clientInfo.remoteAddress,
                                    url,
                                    b64
                                )

                            val tmpFile = File("$uuid.tmp")
                            val tmpLog = File("$b64-$uuid.log")
                            val ffmpegLog = File("$b64-$uuid.log")
                            val endGoalTmp = File(tmpFile.absolutePath.replace(".tmp", ".$format"))

                            try {
                                withContext(Dispatchers.IO) {
                                    val downloadProcess =
                                        ProcessBuilder().command(ArrayList(YoutubeAudioSource.command).apply {
                                            add(url)
                                            add(tmpFile.absolutePath)
                                            add(YoutubeAudioSource.format)
                                        }).redirectErrorStream(true).redirectOutput(tmpLog).start()

                                    if (!downloadProcess.waitFor(90, TimeUnit.SECONDS)) {
                                        downloadProcess.destroyForcibly().waitFor()
                                        logger.warn(
                                            "[{}] Forcibly destroyed the download process for {}",
                                            context.clientInfo.userUID,
                                            url,
                                            true
                                        )
                                    }
                                }

                                if (!endGoalTmp.exists()) {
                                    try {
                                        logger.info(
                                            "[{}] {} does not exist, attempting to convert with ffmpeg",
                                            context.clientInfo.userUID,
                                            endGoalTmp
                                        )

                                        if (!tmpFile.exists()) {
                                            val lastLine = tmpLog.useLines { seq -> seq.last() }
                                            return logger.error(
                                                "[{}] {} does not exist, what happened? (Last line was {})",
                                                context.clientInfo.userUID,
                                                tmpFile,
                                                lastLine,
                                                true
                                            )
                                        }

                                        if (MediaWrapper.ffmpeg.installed) {
                                            if (!MediaWrapper.ffmpeg.convert(tmpFile, endGoalTmp, ffmpegLog))
                                                return logger.error(
                                                    "[{}] Failed to convert {} to {}",
                                                    context.clientInfo.userUID,
                                                    tmpFile,
                                                    endGoalTmp
                                                )

                                            if (!endGoalTmp.exists())
                                                return logger.error(
                                                    "[{}] {} still does not exist, what happened?",
                                                    context.clientInfo.userUID,
                                                    endGoalTmp
                                                )
                                        } else
                                            return logger.error(
                                                "[{}] ffmpeg not installed, nothing we can do",
                                                context.clientInfo.userUID
                                            )
                                    } finally {
                                        context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                                            .setStatusCode(500).end()
                                    }
                                }

                                withContext(Dispatchers.IO) {
                                    endGoalTmp.useThenDelete {
                                        EternalJukebox.storage.store(
                                            "$b64.${YoutubeAudioSource.format}",
                                            EnumStorageType.EXTERNAL_AUDIO,
                                            FileDataSource(it),
                                            YoutubeAudioSource.mimes[YoutubeAudioSource.format]
                                                ?: "audio/mpeg",
                                            context.clientInfo
                                        )
                                    }
                                }

                                if (EternalJukebox.storage.provide(
                                        "$b64.$format",
                                        EnumStorageType.EXTERNAL_AUDIO,
                                        context,
                                        context.clientInfo
                                    )
                                ) return

                                val data = EternalJukebox.storage.provide(
                                    "$b64.$format",
                                    EnumStorageType.EXTERNAL_AUDIO,
                                    context.clientInfo
                                )
                                if (data != null)
                                    return context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                                        .end(data, AudioAPI.mime)
                            } finally {
                                tmpFile.guaranteeDelete()
                                withContext(Dispatchers.IO) {
                                    tmpLog.useThenDelete {
                                        EternalJukebox.storage.store(
                                            it.name,
                                            EnumStorageType.LOG,
                                            FileDataSource(it),
                                            "text/plain",
                                            context.clientInfo
                                        )
                                    }
                                }
                                withContext(Dispatchers.IO) {
                                    ffmpegLog.useThenDelete {
                                        EternalJukebox.storage.store(
                                            it.name,
                                            EnumStorageType.LOG,
                                            FileDataSource(it),
                                            "text/plain",
                                            context.clientInfo
                                        )
                                    }
                                }
                                withContext(Dispatchers.IO) {
                                    endGoalTmp.useThenDelete {
                                        EternalJukebox.storage.store(
                                            "$b64.$format",
                                            EnumStorageType.EXTERNAL_AUDIO,
                                            FileDataSource(it),
                                            YoutubeAudioSource.mimes[format]
                                                ?: "audio/mpeg",
                                            context.clientInfo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            context.reroute(
                "/api" + mountPath + "/jukebox/${context.request().getParam("fallbackID")
                    ?: "7GhIk7Il098yCjg4BQjzvb"}"
            )
        }
    }

    suspend fun upload(context: RoutingContext) {
        if (!EternalJukebox.storage.shouldStore(EnumStorageType.UPLOADED_AUDIO)) {
            return context.endWithStatusCode(502) {
                this["error"] = "This server does not support uploaded audio"
            }
        } else if (context.fileUploads().isEmpty()) {
            return context.endWithStatusCode(400) {
                this["error"] = "No file uploads"
            }
        } else {
            val file = context.fileUploads().first()

            val ffmpegLog = File("${file.fileName()}-$uuid.log")
            val starting = File(file.uploadedFileName())
            val ending = File("$uuid.$format")

            try {
                if (MediaWrapper.ffmpeg.installed) {
                    if (!MediaWrapper.ffmpeg.convert(starting, ending, ffmpegLog))
                        return logger.error(
                            "[{}] Failed to convert {} to {}",
                            context.clientInfo.userUID,
                            starting,
                            ending
                        )

                    if (!ending.exists())
                        return logger.error("[{}] {} does not exist, what happened?", context.clientInfo.userUID, ending)
                } else
                    return logger.error("[{}] ffmpeg not installed, nothing we can do")
            } finally {
                starting.guaranteeDelete()
                withContext(Dispatchers.IO) {
                    ffmpegLog.useThenDelete {
                        EternalJukebox.storage.store(
                            it.name,
                            EnumStorageType.LOG,
                            FileDataSource(it),
                            "text/plain",
                            context.clientInfo
                        )
                    }
                }

                val hash = ending.useThenDelete { endingFile ->
                    val hash = FileInputStream(endingFile).use { stream -> stream.sha512Hash() }
                    withContext(Dispatchers.IO) {
                        EternalJukebox.storage.store(
                            "$hash.$format",
                            EnumStorageType.UPLOADED_AUDIO,
                            FileDataSource(endingFile),
                            YoutubeAudioSource.mimes[format] ?: "audio/mpeg",
                            context.clientInfo
                        )
                    }

                    return@useThenDelete hash
                } ?: return context.endWithStatusCode(502) { this["error"] = "ffmpeg goal file does not exist" }

                context.endWithStatusCode(201) { this["id"] = hash }
            }
        }
    }

    fun Fuel.headOrGet(url: String): Pair<Request, Response> {
        val (headRequest, headResponse) = Fuel.head(url).response()

        if (headResponse.statusCode == 404) {
            val (getRequest, getResponse) = Fuel.get(url).response()

            if (headResponse.statusCode != getResponse.statusCode)
                logger.warn("Request to {} gave a different response between HEAD and GET request ({} vs {})", url, headResponse.statusCode, getResponse.statusCode)

            return getRequest to getResponse
        }

        return headRequest to headResponse
    }

    init {
        logger.info("Initialised Audio Api")
    }
}