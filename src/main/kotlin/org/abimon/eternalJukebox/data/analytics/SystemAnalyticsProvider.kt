package org.abimon.eternalJukebox.data.analytics

import com.sun.management.OperatingSystemMXBean
import io.vertx.ext.web.Router
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.visi.lang.usedMemory
import java.lang.management.ManagementFactory

object SystemAnalyticsProvider: IAnalyticsProvider {
    val startup = System.currentTimeMillis()
    val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    override fun shouldProvide(type: EnumAnalyticType<*>): Boolean = true

    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    override fun <T: Any> provide(now: Long, type: EnumAnalyticType<T>): T? {
        return when (type) {
            is EnumAnalyticType.UPTIME -> now - startup
            is EnumAnalyticType.TOTAL_MEMORY -> Runtime.getRuntime().totalMemory()
            is EnumAnalyticType.FREE_MEMORY -> Runtime.getRuntime().freeMemory()
            is EnumAnalyticType.USED_MEMORY -> Runtime.getRuntime().usedMemory()

            is EnumAnalyticType.PROCESS_CPU_LOAD -> osBean.processCpuLoad
            is EnumAnalyticType.SYSTEM_CPU_LOAD -> osBean.systemCpuLoad

            else -> return null
        } as? T
    }

    override fun setupWebAnalytics(router: Router) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}