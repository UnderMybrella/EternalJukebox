package org.abimon.eternalJukebox.handlers.api

import io.vertx.core.json.JsonArray
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.ByteArrayDataSource
import org.slf4j.LoggerFactory

object AnalysisAPI : IAPI {
    override val mountPath: String = "/analysis"
    override val name: String = "ANALYSIS"
    val logger = LoggerFactory.getLogger("AnalysisApi")

    override fun setup(router: Router) {
        router.get("/analyse/:id").suspendingHandler(this::analyseSpotify)
        router.get("/search").suspendingHandler(AnalysisAPI::searchSpotify)
    }

    suspend fun analyseSpotify(context: RoutingContext) {
        if (EternalJukebox.storage.shouldStore(EnumStorageType.ANALYSIS)) {
            val id = context.pathParam("id")
            val update = context.request().getParam("update")?.toBoolean() ?: false
            if (EternalJukebox.storage.isStored("$id.json", EnumStorageType.ANALYSIS) && !update) {
                if (EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context, context.clientInfo))
                    return

                val data = EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context.clientInfo)
                if (data != null)
                    return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(data, "application/json")
            }

            if (update)
                logger.info("[{}] {} is requesting an update for {}", context.clientInfo.userUID, context.clientInfo.remoteAddress, id)

            val track = EternalJukebox.spotify.analyse(id, context.clientInfo)

            if (track == null)
                context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(jsonObjectOf(
                        "error" to "Track object is null",
                        "client_uid" to context.clientInfo.userUID
                ))
            else {
                context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(track.toJsonObject())

                withContext(Dispatchers.IO) {
                    EternalJukebox.storage.store(
                        "$id.json",
                        EnumStorageType.ANALYSIS,
                        ByteArrayDataSource(track.toJsonObject().toString().toByteArray(Charsets.UTF_8)),
                        "application/json",
                        context.clientInfo
                    )
                }
            }
        } else {
            context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(501).end(jsonObjectOf(
                    "error" to "Configured storage method does not support storing ANALYSIS",
                    "client_uid" to context.clientInfo.userUID
            ))
        }
    }

    suspend fun searchSpotify(context: RoutingContext) {
        val query = context.request().getParam("query") ?: "Never Gonna Give You Up"
        val results = EternalJukebox.spotify.search(query, context.clientInfo)

        context.response().end(JsonArray(results.map(JukeboxInfo::toJsonObject)))
    }

    init {
        logger.info("Initialised Analysis Api")
    }
}