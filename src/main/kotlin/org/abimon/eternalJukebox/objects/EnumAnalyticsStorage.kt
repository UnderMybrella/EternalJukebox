package org.abimon.eternalJukebox.objects

import org.abimon.eternalJukebox.data.analytics.IAnalyticsStorage
import org.abimon.eternalJukebox.data.analytics.LocalAnalyticStorage
import kotlin.reflect.KClass

enum class EnumAnalyticsStorage(val klass: KClass<out IAnalyticsStorage>) {
    LOCAL(LocalAnalyticStorage::class);

    val analytics: IAnalyticsStorage
        get() = klass.objectInstance!!
}