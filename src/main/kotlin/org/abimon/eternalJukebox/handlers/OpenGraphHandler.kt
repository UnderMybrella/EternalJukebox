package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
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
        val info = EternalJukebox.spotify.getInfo(id) ?: return context.next()
        doc.select("meta[property=og:title]").first().attr("content", "Eternal Jukebox for ${info.title} by ${info.artist}")
        doc.select("meta[property=og:description]").first().attr("content", "For when ${info.title} just isn't long enough")
        doc.select("meta[property=og:url]").first().attr("content", context.request().absoluteURI())

        context.response().putHeader("Content-Type", "text/html;charset=UTF-8").end(doc.outerHtml())
    }
}