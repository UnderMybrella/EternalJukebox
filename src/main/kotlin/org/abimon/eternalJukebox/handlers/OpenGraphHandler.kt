package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

object OpenGraphHandler {
    fun setup(router: Router) {
        router.get("/jukebox_go.html").handler(OpenGraphHandler::jukeboxOG)
    }

    fun jukeboxOG(context: RoutingContext) {

    }
}