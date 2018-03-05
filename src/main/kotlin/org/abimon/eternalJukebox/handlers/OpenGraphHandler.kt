package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.clientInfo
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.objects.JukeboxTrack
import org.jsoup.Jsoup
import java.io.File

object OpenGraphHandler {
    val webRoot = File(EternalJukebox.config.webRoot)

    fun setup(router: Router) {
        router.get("/jukebox_go.html").handler(OpenGraphHandler::jukeboxOG)
    }

    fun jukeboxOG(context: RoutingContext) {
        val doc = Jsoup.parse(File(webRoot, "jukebox_go.html"), "UTF-8")
        val id = context.request().getParam("id") ?: "7GhIk7Il098yCjg4BQjzvb"
        if(EternalJukebox.storage.shouldStore(EnumStorageType.ANALYSIS) && EternalJukebox.storage.isStored("$id.json", EnumStorageType.ANALYSIS)) {
            val info = EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context.clientInfo)?.use { inputStream ->
                return@use EternalJukebox.jsonMapper.readValue(inputStream, JukeboxTrack::class.java).info
            } ?: return context.next()

            doc.select("meta[property=og:title]").first().attr("content", "Eternal Jukebox for ${info.title} by ${info.artist}")
            doc.select("meta[property=og:description]").first().attr("content", "For when ${info.title} just isn't long enough")
            doc.select("meta[property=og:url]").first().attr("content", context.request().absoluteURI())

            context.response().putHeader("X-Client-UID", context.clientInfo.userUID).putHeader("Content-Type", "text/html;charset=UTF-8").end(doc.outerHtml())
        } else {
            context.next()
        }
    }

    init {
        log("Initialised Open Graph")
    }
}