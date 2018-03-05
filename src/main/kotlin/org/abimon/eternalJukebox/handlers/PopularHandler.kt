package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.clientInfo

object PopularHandler {

    fun setup(router: Router) {
        router.get("/jukebox_go.html").handler(this::makeJukeboxPopular)
        router.get("/canonizer_go.html").handler(this::makeCanonizerPopular)
    }

    fun makeJukeboxPopular(context: RoutingContext) {
        val clientInfo = context.clientInfo
        val id = context.request().getParam("id") ?: return context.next()
        if (EternalJukebox.spotify.getInfo(id, clientInfo) != null)
            EternalJukebox.database.makeSongPopular("jukebox", id, clientInfo)

        context.next()
    }

    fun makeCanonizerPopular(context: RoutingContext) {
        val clientInfo = context.clientInfo
        val id = context.request().getParam("id") ?: return context.next()
        if (EternalJukebox.spotify.getInfo(id, clientInfo) != null)
            EternalJukebox.database.makeSongPopular("canonizer", id, clientInfo)

        context.next()
    }
}