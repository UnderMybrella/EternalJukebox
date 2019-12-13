package dev.eternalbox.eternaljukebox.data

import dev.eternalbox.eternaljukebox.EternalJukebox
import io.vertx.ext.web.RoutingContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class JukeboxRoutingContext(val jukebox: EternalJukebox, val context: RoutingContext): RoutingContext by context {
    val vertx
        get() = jukebox.vertx
    val httpServer
        get() = jukebox.httpServer
    val baseRouter
        get() = jukebox.baseRouter
    val apiRouter
        get() = jukebox.apiRouter
    val languageData
        get() = jukebox.languageData

    fun errorMessage(key: String, vararg params: Any?): String = languageData.errorMessageArray(acceptableLanguages(), key, params)
}