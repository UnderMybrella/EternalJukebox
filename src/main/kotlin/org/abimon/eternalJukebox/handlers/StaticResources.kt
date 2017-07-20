package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler

object StaticResources {

    fun setup(router: Router) {
        router.get().handler(StaticHandler.create("web"))
    }
}