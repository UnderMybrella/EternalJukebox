package org.abimon.eternalJukebox.handlers.api

import io.vertx.ext.web.Router

interface IAPI {
    /**
     * Path to mount this API on. Must start with `/`
     */
    val mountPath: String

    /**
     * Used to identify this component
     */
    val name: String

    fun setup(router: Router)

    /**
     * Is this API working properly?
     */
    fun test(): Boolean = true
}