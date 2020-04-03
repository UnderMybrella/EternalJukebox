package org.abimon.eternalJukebox.handlers.api

import com.jakewharton.fliptables.FlipTable
import com.sun.management.OperatingSystemMXBean
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.units.data.ByteUnit
import org.abimon.visi.lang.usedMemory
import org.abimon.visi.time.timeDifference
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import java.time.LocalDateTime

object SiteAPI: IAPI {
    override val mountPath: String = "/site"
    override val name: String = "Site"
    val startupTime: LocalDateTime = LocalDateTime.now()

    val memoryFormat = DecimalFormat("####.##")
    val cpuFormat = DecimalFormat("#.####")

    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    override fun setup(router: Router) {
        router.get("/healthy").handler { it.response().end("Up for ${startupTime.timeDifference()}") }
        router.get("/usage").handler(SiteAPI::usage)

        router.post().handler(BodyHandler.create().setBodyLimit(1 * 1000 * 1000).setDeleteUploadedFilesOnEnd(true))

        router.get("/expand/:id").suspendingHandler(SiteAPI::expand)
        router.get("/expand/:id/redirect").suspendingHandler(SiteAPI::expandAndRedirect)
        router.post("/shrink").handler(SiteAPI::shrink)

        router.get("/popular/:service").handler(this::popular)
    }

    fun popular(context: RoutingContext) {
        val service = context.pathParam("service")
        val count = context.request().getParam("count")?.toIntOrNull() ?: context.request().getParam("limit")?.toIntOrNull() ?: 10

        context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(JsonArray(EternalJukebox.database.providePopularSongs(service, count, context.clientInfo)))
    }

    fun usage(context: RoutingContext) {
        val rows = arrayOf(
                "Uptime" to startupTime.timeDifference().format(),
                "Total Memory" to ByteUnit(Runtime.getRuntime().totalMemory()).toMegabytes().format(memoryFormat),
                "Free Memory" to ByteUnit(Runtime.getRuntime().freeMemory()).toMegabytes().format(memoryFormat),
                "Used Memory" to ByteUnit(Runtime.getRuntime().usedMemory()).toMegabytes().format(memoryFormat),
                "CPU Load (Process)" to "${cpuFormat.format(osBean.processCpuLoad)}%",
                "CPU Load (System)" to "${cpuFormat.format(osBean.systemCpuLoad)}%"
        )

        context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(FlipTable.of(arrayOf("Key", "Value"), rows.map { (one, two) -> arrayOf(one, two) }.toTypedArray()))
    }

    suspend fun expand(context: RoutingContext) {
        val id = context.pathParam("id")
        val clientInfo = context.clientInfo
        val expanded = expand(id, clientInfo) ?: return context.response().putHeader("X-Client-UID", clientInfo.userUID).setStatusCode(400).end(jsonObjectOf("error" to "No short ID stored", "id" to id))
        context.response().end(expanded)
    }

    suspend fun expandAndRedirect(context: RoutingContext) {
        val id = context.pathParam("id")
        val clientInfo = context.clientInfo
        val expanded = expand(id, clientInfo) ?: return context.response().putHeader("X-Client-UID", clientInfo.userUID).setStatusCode(400).end(jsonObjectOf("error" to "No short ID stored", "id" to id))

        context.response().redirect(expanded.getString("url"))
    }

    suspend fun expand(id: String, clientInfo: ClientInfo): JsonObject? {
        val params = EternalJukebox.database.expandShortURL(id, clientInfo) ?: return null
        val paramsMap = params.map { pair -> pair.split('=', limit = 2) }.filter { pair -> pair.size == 2 }.map { pair -> Pair(pair[0], pair[1]) }.toMap(HashMap())

        val service = paramsMap.remove("service") ?: "jukebox"
        val response = JsonObject()

        when(service.toLowerCase()) {
            "jukebox" -> response["url"] = "/jukebox_go.html?${paramsMap.entries.joinToString("&") { (key, value) -> "$key=$value" } }"
            "canonizer" -> response["url"] = "/canonizer_go.html?${paramsMap.entries.joinToString("&") { (key, value) -> "$key=$value" } }"

            else -> response["url"] = "/jukebox_index.html"
        }

        val trackInfo = EternalJukebox.spotify.getInfo(paramsMap["id"] ?: "4uLU6hMCjMI75M1A2tKUQC", clientInfo)

        response["song"] = EternalJukebox.jsonMapper.convertValue(trackInfo, Map::class.java)
        response["params"] = paramsMap

        return response
    }

    fun shrink(context: RoutingContext) {
        val params = context.bodyAsString.split('&').toTypedArray()
        val id = EternalJukebox.database.provideShortURL(params, context.clientInfo)
        context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(jsonObjectOf("id" to id, "params" to params))
    }

    init {
        GlobalScope.launch {
            while (isActive) {
                val time = System.currentTimeMillis()

                try {
                    EternalJukebox.analyticsProviders.forEach { provider ->
                        EternalJukebox.analytics.storeMultiple(time, provider.provideMultiple(time, *EnumAnalyticType.VALUES.filter { type -> provider.shouldProvide(type) }.toTypedArray()).map { (type, data) -> type to data })
                    }
                } catch (th: Throwable) {
                    th.printStackTrace()
                }

                delay(EternalJukebox.config.usageWritePeriod)
            }
        }
    }
}