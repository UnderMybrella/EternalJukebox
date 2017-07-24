package org.abimon.eternalJukebox.handlers.api

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.end
import org.abimon.eternalJukebox.jsonObject
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.objects.EnumStorageType

object AudioAPI: IAPI {
    override val mountPath: String = "/audio"
    override val name: String = "Audio"
    val format: String
        get() = EternalJukebox.config.audioSourceOptions["AUDIO_FORMAT"] as? String ?: "m4a"

    val mime: String
        get() = EternalJukebox.config.audioSourceOptions["AUDIO_MIME"] as? String ?: run {
            when(format) {
                "m4a" -> return@run "audi/mp4"
                "aac" -> return@run "audio/aac"
                "mp3" -> return@run "audio/mpeg"
                "ogg" -> return@run "audio/ogg"
                "wav" -> return@run "audio/wav"
                else -> return@run "audio/mpeg"
            }
        }

    override fun setup(router: Router) {
        router.get("/jukebox/:id").blockingHandler(AudioAPI::jukeboxAudio)
    }

    fun jukeboxAudio(context: RoutingContext) {
        val id = context.pathParam("id")
        val update = context.request().getParam("update")?.toBoolean() ?: false
        if(EternalJukebox.storage.isStored("$id.$format", EnumStorageType.AUDIO) && !update) {
            if(EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO, context))
                return

            val data = EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO)
            if(data != null)
                return context.response().end(data, mime)
        }

        if(update)
            log("${context.request().connection().remoteAddress()} is requesting an update for $id")

        val track = EternalJukebox.spotify.getInfo(id) ?: run {
            log("No track info for $id; returning 404")
            return context.fail(404)
        }

        val audio = EternalJukebox.audio.provide(track)

        if(audio == null)
            context.response().setStatusCode(400).end(jsonObject {
                put("error", "Audio is null")
            })
        else {
            if(EternalJukebox.storage.isStored("$id.$format", EnumStorageType.AUDIO) && !update) {
                if(EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO, context))
                    return

                val data = EternalJukebox.storage.provide("$id.$format", EnumStorageType.AUDIO)
                if(data != null)
                    return context.response().end(data, mime)
            }

            return context.response().end(audio, mime)
        }
    }
}