package org.abimon.eternalJukebox.data.analytics

import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.simpleClassName
import org.abimon.eternalJukebox.useThenDelete
import org.abimon.visi.io.FileDataSource
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*
import kotlin.collections.HashMap

object LocalAnalyticStorage : IAnalyticsStorage {
    val storageLocations: Map<EnumAnalyticType<*>, File> = EnumAnalyticType.VALUES.map { type -> type to File(EternalJukebox.config.analyticsStorageOptions["${type::class.simpleClassName.toUpperCase()}_FILE"] as? String ?: "analytics-${type::class.simpleClassName.toLowerCase()}.log") }.toMap()
    val storageStreams: MutableMap<EnumAnalyticType<*>, PrintStream> = HashMap()

    override fun shouldStore(type: EnumAnalyticType<*>): Boolean = true
    override fun <T : Any> store(now: Long, data: T, type: EnumAnalyticType<T>): Boolean {
        if(!storageStreams.containsKey(type))
            storageStreams[type] = PrintStream(FileOutputStream(storageLocations[type] ?: return false), true)
        storageStreams[type]?.println("$now|$data") ?: return false
        return true
    }

    init {
        storageLocations.forEach { (type, log) ->
            if (log.exists()) {
                if (EternalJukebox.storage.shouldStore(EnumStorageType.LOG)) {
                    log.useThenDelete { file -> EternalJukebox.storage.store("Analysis-${type::class.simpleClassName}-${UUID.randomUUID()}.log", EnumStorageType.LOG, FileDataSource(file), null) }
                } else {
                    log.delete()
                }
            }

            log.createNewFile()
        }
    }
}