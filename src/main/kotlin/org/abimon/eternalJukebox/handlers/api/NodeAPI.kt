package org.abimon.eternalJukebox.handlers.api

import io.vertx.ext.web.Router

object NodeAPI: IAPI {
    override val mountPath: String = "/node"
    override val name: String = "Nodes"

    override fun setup(router: Router) {
        router.route("/healthy").handler { context -> context.response().end() }
        router.route("/audio/:id").blockingHandler { context -> context.reroute("/api/audio/jukebox/${context.pathParam("id")}") }
    }
}