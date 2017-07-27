package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.log

object StaticResources {
    fun setup(router: Router) {
        router.get().handler(StaticHandler.create(EternalJukebox.config.webRoot))
    }

    init {
        log("Initialised Static Resources")
    }
}