package org.abimon.eternalJukebox.data.analytics

import io.vertx.ext.web.Router
import org.abimon.eternalJukebox.objects.EnumAnalyticType

interface IAnalyticsProvider {
    /**
     * Should we store this type of analytics data?
     */
    fun shouldProvide(type: EnumAnalyticType<*>): Boolean

    /**
     * Provides the requested data for this timeframe
     */
    fun <T: Any> provide(now: Long, type: EnumAnalyticType<T>): T?

    /**
     * Provides the requested data for this timeframe
     */
    fun provideMultiple(now: Long, vararg types: EnumAnalyticType<*>): Map<EnumAnalyticType<*>, Any> {
        val map: MutableMap<EnumAnalyticType<*>, Any> = HashMap()

        for (type in types)
            map[type] = provide(now, type) ?: continue

        return map
    }

    fun setupWebAnalytics(router: Router)
}