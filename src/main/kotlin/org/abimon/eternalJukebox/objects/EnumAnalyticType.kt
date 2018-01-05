package org.abimon.eternalJukebox.objects

import kotlin.reflect.KClass

sealed class EnumAnalyticType<T: Any>(val klass: KClass<T>) {
    companion object {
        val VALUES = arrayOf(
                UPTIME,
                TOTAL_MEMORY, FREE_MEMORY, USED_MEMORY,
                PROCESS_CPU_LOAD, SYSTEM_CPU_LOAD
        )
    }

    object UPTIME: EnumAnalyticType<Long>(Long::class)
    object TOTAL_MEMORY: EnumAnalyticType<Long>(Long::class)
    object FREE_MEMORY: EnumAnalyticType<Long>(Long::class)
    object USED_MEMORY: EnumAnalyticType<Long>(Long::class)
    object PROCESS_CPU_LOAD: EnumAnalyticType<Float>(Float::class)
    object SYSTEM_CPU_LOAD: EnumAnalyticType<Float>(Float::class)
    object SESSION_REQUESTS: EnumAnalyticType<Long>(Long::class)
    object HOURLY_REQUESTS: EnumAnalyticType<Long>(Long::class)
    object UNIQUE_SESSION_VISITORS: EnumAnalyticType<Long>(Long::class)
    object UNIQUE_HOURLY_VISITORS: EnumAnalyticType<Long>(Long::class)
    object NEW_ANALYSIS_REQUESTS: EnumAnalyticType<Long>(Long::class)
    object NEW_AUDIO_REQUESTS: EnumAnalyticType<Long>(Long::class)
}