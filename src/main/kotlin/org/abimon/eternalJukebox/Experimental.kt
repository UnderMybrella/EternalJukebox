package org.abimon.eternalJukebox

import io.vertx.ext.web.Router
import java.io.File

object Experimental {

    fun setup(router: Router) {
        router.get("/experimental/*").handler(StaticFileHandler("/experimental", File("experimental")))
    }
}