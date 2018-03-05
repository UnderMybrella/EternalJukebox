package org.abimon.eternalJukebox.objects

import org.abimon.eternalJukebox.data.analytics.HTTPAnalyticsProvider
import org.abimon.eternalJukebox.data.analytics.IAnalyticsProvider
import org.abimon.eternalJukebox.data.analytics.SystemAnalyticsProvider
import kotlin.reflect.KClass

enum class EnumAnalyticsProvider(val klass: KClass<out IAnalyticsProvider>) {
    SYSTEM(SystemAnalyticsProvider::class),
    HTTP(HTTPAnalyticsProvider::class);

    val provider: IAnalyticsProvider
        get() = klass.objectInstance!!
}