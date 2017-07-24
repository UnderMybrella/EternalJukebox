package org.abimon.eternalJukebox.handlers.api

import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.end
import org.abimon.eternalJukebox.jsonObject
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.eternalJukebox.toJsonObject

object AnalysisAPI: IAPI {
    override val mountPath: String = "/analysis"
    override val name: String = "ANALYSIS"

    override fun setup(router: Router) {
        router.get("/analyse/:id").blockingHandler(AnalysisAPI::analyseSpotify)
        router.get("/search").blockingHandler(AnalysisAPI::searchSpotify)
    }

    fun analyseSpotify(context: RoutingContext) {
        val id = context.pathParam("id")
        val track = EternalJukebox.spotify.analyse(id)

        if(track == null)
            context.response().setStatusCode(400).end(jsonObject {
                put("error", "Track object is null")
            })
        else
            context.response().end(track.toJsonObject())
    }

    fun searchSpotify(context: RoutingContext) {
        val query = context.request().getParam("query") ?: "Never Gonna Give You Up"
        val results = EternalJukebox.spotify.search(query)

        context.response().end(JsonArray(results.map(JukeboxInfo::toJsonObject)))
    }
}