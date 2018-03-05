package org.abimon.eternalJukebox.objects

sealed class EnumAnalyticType<T : Any> {
    companion object {
        val VALUES: Array<EnumAnalyticType<*>> by lazy {
            arrayOf(
                    UPTIME,
                    TOTAL_MEMORY, FREE_MEMORY, USED_MEMORY,
                    FREE_MEMORY_PERCENT, USED_MEMORY_PERCENT,
                    PROCESS_CPU_LOAD, SYSTEM_CPU_LOAD,
                    SESSION_REQUESTS, HOURLY_REQUESTS
            )
        }
    }

    object UPTIME : EnumAnalyticType<Long>()
    object TOTAL_MEMORY : EnumAnalyticType<Long>()
    object FREE_MEMORY : EnumAnalyticType<Long>()
    object USED_MEMORY : EnumAnalyticType<Long>()
    object FREE_MEMORY_PERCENT : EnumAnalyticType<Float>()
    object USED_MEMORY_PERCENT : EnumAnalyticType<Float>()
    object PROCESS_CPU_LOAD : EnumAnalyticType<Float>()
    object SYSTEM_CPU_LOAD : EnumAnalyticType<Float>()
    object SESSION_REQUESTS : EnumAnalyticType<Long>()
    object HOURLY_REQUESTS : EnumAnalyticType<Long>()
    object UNIQUE_SESSION_VISITORS : EnumAnalyticType<Long>()
    object UNIQUE_HOURLY_VISITORS : EnumAnalyticType<Long>()
    object NEW_ANALYSIS_REQUESTS : EnumAnalyticType<Long>()
    object NEW_AUDIO_REQUESTS : EnumAnalyticType<Long>()
}