package org.abimon.eternalJukebox.data.analytics

import org.abimon.eternalJukebox.objects.EnumAnalyticType

interface IAnalyticsStorage {
    /**
     * Should we store this type of analytics data?
     */
    fun shouldStore(type: EnumAnalyticType<*>): Boolean

    /**
     * Store [data] under [name], as type [type]
     * Returns true if successfully stored; false otherwise
     */
    fun <T: Any> store(now: Long, data: T, type: EnumAnalyticType<T>): Boolean

    @Suppress("UNCHECKED_CAST")
    fun storeMultiple(now: Long, data: List<Pair<EnumAnalyticType<*>, Any>>) = data.forEach { (type, data) -> store(now, data, type as EnumAnalyticType<Any>) }

    @Suppress("UNCHECKED_CAST")
    fun storeGeneric(now: Long, data: Any, type: EnumAnalyticType<*>): Boolean = store(now, data, type as EnumAnalyticType<Any>)
}