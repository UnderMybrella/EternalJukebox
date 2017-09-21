package org.abimon.eternalJukebox.handlers.api

import com.jakewharton.fliptables.FlipTable
import com.sun.management.OperatingSystemMXBean
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.useThenDelete
import org.abimon.units.data.ByteUnit
import org.abimon.visi.io.FileDataSource
import org.abimon.visi.lang.usedMemory
import org.abimon.visi.time.timeDifference
import java.io.File
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate


object SiteAPI: IAPI {
    override val mountPath: String = "/site"
    override val name: String = "Site"
    val startupTime: LocalDateTime = LocalDateTime.now()

    val memoryFormat = DecimalFormat("####.##")
    val cpuFormat = DecimalFormat("#.####")

    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    val usageTimer: Timer = Timer()

    val currentMemory = File("current-memory-usage.txt")
    val currentCPU = File("current-cpu-usage.txt")
    val globalRequests = File("global-request-count.txt")
    val hourlyRequests = File("hourly-request-count.txt")
    val globalUniqueVisitors = File("global-unique-visitor-count.txt")
    val hourlyUniqueVisitors = File("hourly-unique-visitor-count.txt")

    val memoryUsageOut: PrintStream
    val cpuUsageOut: PrintStream
    val globalRequestsOut: PrintStream
    val hourlyRequestsOut: PrintStream
    val globalUniqueVisitorsOut: PrintStream
    val hourlyUniqueVisitorsOut: PrintStream

    override fun setup(router: Router) {
        router.get("/healthy").handler { it.response().end("Up for ${startupTime.timeDifference()}") }
        router.get("/usage").handler(SiteAPI::usage)
        router.get("/mem_graph").blockingHandler(SiteAPI::graphMemory)
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

        context.response().end(FlipTable.of(arrayOf("Key", "Value"), rows.map { (one, two) -> arrayOf(one, two) }.toTypedArray()))
    }

    fun graphMemory(context: RoutingContext) {
//        val graphMx = mxGraph()
//        val parent = graphMx.defaultParent
//        graphMx.model.beginUpdate()
//
//        var prev: Any? = null
//        var first: Long? = null
//
//        currentMemory.forEachLine { line ->
//            val (time, used) = line.split('|')
//            if(first == null)
//                first = time.toLong()
//
//            val offset = (time.toLong() - first!!) / 50
//
//            val curr = graphMx.insertVertex(parent, null, null, offset.toDouble(), used.toDouble(), 10.0, 10.0)
//            if(prev != null)
//                graphMx.insertEdge(parent, null, null, prev, curr)
//            prev = curr
//        }
//
//        graphMx.model.endUpdate()
//
//        val image = mxCellRenderer.createBufferedImage(graphMx, null, 1.0, Color.WHITE, true, null)
//
//        val baos = ByteArrayOutputStream()
//        ImageIO.write(image, "PNG", baos)
//
//        context.response().end(ByteArrayDataSource(baos.toByteArray()), "image/png")

        context.response().setStatusCode(501).end("Not Implemented Yet™")
    }

    init {
        if(currentMemory.exists())
            currentMemory.useThenDelete { EternalJukebox.storage.store("Memory-Usage-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        memoryUsageOut = PrintStream(currentMemory)

        if(currentCPU.exists())
            currentCPU.useThenDelete { EternalJukebox.storage.store("CPU-Usage-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        cpuUsageOut = PrintStream(currentCPU)

        if(globalRequests.exists())
            globalRequests.useThenDelete { EternalJukebox.storage.store("Global-Request-Count-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        globalRequestsOut = PrintStream(globalRequests)

        if(hourlyRequests.exists())
            hourlyRequests.useThenDelete { EternalJukebox.storage.store("Hourly-Request-Count-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        hourlyRequestsOut = PrintStream(hourlyRequests)

        if(globalUniqueVisitors.exists())
            globalUniqueVisitors.useThenDelete { EternalJukebox.storage.store("Global-Unique-Visitor-Count-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        globalUniqueVisitorsOut = PrintStream(globalUniqueVisitors)

        if(hourlyUniqueVisitors.exists())
            hourlyUniqueVisitors.useThenDelete { EternalJukebox.storage.store("Hourly-Unique-Visitor-Count-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(it)) }
        hourlyUniqueVisitorsOut = PrintStream(hourlyUniqueVisitors)

        usageTimer.scheduleAtFixedRate(0, EternalJukebox.config.usageWritePeriod) {
            val current = System.currentTimeMillis()
            memoryUsageOut.println("$current|${memoryFormat.format(ByteUnit(Runtime.getRuntime().usedMemory()).toMegabytes().megabytes)}")
            cpuUsageOut.println("$current|${cpuFormat.format(osBean.processCpuLoad)}")
            globalRequestsOut.println("$current|${EternalJukebox.requests.get()}")
            hourlyRequestsOut.println("$current|${EternalJukebox.hourlyRequests.get()}")
            globalUniqueVisitorsOut.println("$current|${EternalJukebox.uniqueVisitors.get()}")
            hourlyUniqueVisitorsOut.println("$current|${EternalJukebox.hourlyUniqueVisitors.get()}")
        }

        log("Initialised Site API")
    }
}