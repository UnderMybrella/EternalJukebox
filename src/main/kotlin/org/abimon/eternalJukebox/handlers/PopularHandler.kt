package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.clientInfo
import org.abimon.eternalJukebox.suspendingHandler

object PopularHandler {

    fun setup(router: Router) {
        router.get("/jukebox_go.html").suspendingHandler(this::makeJukeboxPopular)
        router.get("/canonizer_go.html").suspendingHandler(this::makeCanonizerPopular)
    }

    suspend fun makeJukeboxPopular(context: RoutingContext) {
        val clientInfo = context.clientInfo
        val id = context.request().getParam("id") ?: return context.next()
        if (EternalJukebox.spotify.getInfo(id, clientInfo) != null)
            withContext(Dispatchers.IO) { EternalJukebox.database.makeSongPopular("jukebox", id, clientInfo) }

        context.next()
    }

    suspend fun makeCanonizerPopular(context: RoutingContext) {
        val clientInfo = context.clientInfo
        val id = context.request().getParam("id") ?: return context.next()
        if (EternalJukebox.spotify.getInfo(id, clientInfo) != null)
            withContext(Dispatchers.IO) { EternalJukebox.database.makeSongPopular("canonizer", id, clientInfo) }

        context.next()
    }
}