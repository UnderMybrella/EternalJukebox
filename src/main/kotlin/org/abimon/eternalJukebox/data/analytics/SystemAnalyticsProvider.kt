package org.abimon.eternalJukebox.data.analytics

import com.sun.management.OperatingSystemMXBean
import io.vertx.ext.web.Router
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.visi.lang.usedMemory
import java.lang.management.ManagementFactory

object SystemAnalyticsProvider: IAnalyticsProvider {
    val startup = System.currentTimeMillis()
    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    val PROVIDING = arrayOf(
            EnumAnalyticType.UPTIME,
            EnumAnalyticType.TOTAL_MEMORY, EnumAnalyticType.FREE_MEMORY, EnumAnalyticType.USED_MEMORY,
            EnumAnalyticType.FREE_MEMORY_PERCENT, EnumAnalyticType.USED_MEMORY_PERCENT,
            EnumAnalyticType.PROCESS_CPU_LOAD, EnumAnalyticType.SYSTEM_CPU_LOAD
    )

    override fun shouldProvide(type: EnumAnalyticType<*>): Boolean = type in PROVIDING

    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    override fun <T: Any> provide(now: Long, type: EnumAnalyticType<T>): T? {
        return when (type) {
            is EnumAnalyticType.UPTIME -> now - startup

            is EnumAnalyticType.TOTAL_MEMORY -> Runtime.getRuntime().totalMemory()
            is EnumAnalyticType.FREE_MEMORY -> Runtime.getRuntime().freeMemory()
            is EnumAnalyticType.USED_MEMORY -> Runtime.getRuntime().usedMemory()

            is EnumAnalyticType.FREE_MEMORY_PERCENT -> Runtime.getRuntime().freeMemory() * 100.0f / Runtime.getRuntime().totalMemory()
            is EnumAnalyticType.USED_MEMORY_PERCENT -> Runtime.getRuntime().usedMemory() * 100.0f / Runtime.getRuntime().totalMemory()

            is EnumAnalyticType.PROCESS_CPU_LOAD -> osBean.processCpuLoad
            is EnumAnalyticType.SYSTEM_CPU_LOAD -> osBean.systemCpuLoad

            else -> return null
        } as? T
    }

    override fun setupWebAnalytics(router: Router) {}
}