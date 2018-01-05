package org.abimon.eternalJukebox.handlers.api

import com.jakewharton.fliptables.FlipTable
import com.sun.management.OperatingSystemMXBean
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.clientInfo
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.eternalJukebox.scheduleAtFixedRate
import org.abimon.units.data.ByteUnit
import org.abimon.visi.lang.usedMemory
import org.abimon.visi.time.timeDifference
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

object SiteAPI: IAPI {
    override val mountPath: String = "/site"
    override val name: String = "Site"
    val startupTime: LocalDateTime = LocalDateTime.now()

    val memoryFormat = DecimalFormat("####.##")
    val cpuFormat = DecimalFormat("#.####")

    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    val usageTimer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun setup(router: Router) {
        router.get("/healthy").handler { it.response().end("Up for ${startupTime.timeDifference()}") }
        router.get("/usage").handler(SiteAPI::usage)
    }

    fun usage(context: RoutingContext) {
        val rows = arrayOf(
                "Uptime" to startupTime.timeDifference().format(),
                "Total Memory" to ByteUnit(Runtime.getRuntime().totalMemory()).toMegabytes().format(memoryFormat),
                "Free Memory" to ByteUnit(Runtime.getRuntime().freeMemory()).toMegabytes().format(memoryFormat),
                "Used Memory" to ByteUnit(Runtime.getRuntime().usedMemory()).toMegabytes().format(memoryFormat),
                "CPU Load (Process)" to "${cpuFormat.format(osBean.processCpuLoad)}%",
                "CPU Load (System)" to "${cpuFormat.format(osBean.systemCpuLoad)}%",
                "Requests this session" to "${EternalJukebox.requests.get()}",
                "Requests this hour" to "${EternalJukebox.hourlyRequests.get()}",
                "Unique Visitors this hour" to "${EternalJukebox.hourlyUniqueVisitors.get()}"
        )

        context.response().putHeader("X-Client-UID", context.clientInfo.userUID).end(FlipTable.of(arrayOf("Key", "Value"), rows.map { (one, two) -> arrayOf(one, two) }.toTypedArray()))
    }

    init {
        usageTimer.scheduleAtFixedRate(EternalJukebox.config.usageWritePeriod, EternalJukebox.config.usageWritePeriod) {
            val time = System.currentTimeMillis()

            try {
                EternalJukebox.analyticsProviders.forEach { provider ->
                    provider.provideMultiple(time, *EnumAnalyticType.VALUES.filter { type -> provider.shouldProvide(type) }.toTypedArray()).forEach { type, data ->
                        EternalJukebox.analytics.storeGeneric(time, data, type)
                    }
                }
            } catch (th: Throwable) {
                th.printStackTrace()
            }
        }

        log("Initialised Site API")
    }
}