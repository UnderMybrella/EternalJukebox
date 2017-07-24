package org.abimon.eternalJukebox.handlers.api

import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.ByteArrayDataSource

object AnalysisAPI: IAPI {
    override val mountPath: String = "/analysis"
    override val name: String = "ANALYSIS"

    override fun setup(router: Router) {
        router.get("/analyse/:id").blockingHandler(AnalysisAPI::analyseSpotify)
        router.get("/search").blockingHandler(AnalysisAPI::searchSpotify)
    }

    fun analyseSpotify(context: RoutingContext) {
        val id = context.pathParam("id")
        val update = context.request().getParam("update")?.toBoolean() ?: false
        if(EternalJukebox.storage.isStored("$id.json", EnumStorageType.ANALYSIS) && !update) {
            if(EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context))
                return

            val data = EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS)
            if(data != null)
                return context.response().end(data, "application/json")
        }

        if(update)
            log("${context.request().connection().remoteAddress()} is requesting an update for $id")

        val track = EternalJukebox.spotify.analyse(id)

        if(track == null)
            context.response().setStatusCode(400).end(jsonObject {
                put("error", "Track object is null")
            })
        else {
            context.response().end(track.toJsonObject())

            EternalJukebox.storage.store("$id.json", EnumStorageType.ANALYSIS, ByteArrayDataSource(track.toJsonObject().toString().toByteArray(Charsets.UTF_8)))
        }
    }

    fun searchSpotify(context: RoutingContext) {
        val query = context.request().getParam("query") ?: "Never Gonna Give You Up"
        val results = EternalJukebox.spotify.search(query)

        context.response().end(JsonArray(results.map(JukeboxInfo::toJsonObject)))
    }
}