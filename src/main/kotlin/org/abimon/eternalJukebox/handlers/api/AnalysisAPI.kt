package org.abimon.eternalJukebox.handlers.api

import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.end
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.eternalJukebox.toJsonObject

object AnalysisAPI: IAPI {
    override val mountPath: String = "/analysis"
    override val name: String = "ANALYSIS"

    override fun setup(router: Router) {
        router.get("/analyse/:id").handler(AnalysisAPI::analyseSpotify)
        router.get("/search").handler(AnalysisAPI::searchSpotify)
    }

    fun analyseSpotify(context: RoutingContext) {

    }

    fun searchSpotify(context: RoutingContext) {
        val query = context.request().getParam("query") ?: "Never Gonna Give You Up"
        val results = EternalJukebox.spotify.search(query)

        context.response().end(JsonArray(results.map(JukeboxInfo::toJsonObject)))
    }
}