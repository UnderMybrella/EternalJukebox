package org.abimon.eternalJukebox.data.analytics

import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.EnumAnalyticType
import org.abimon.visi.io.DataSource

interface IAnalyticsStorage {

    /**
     * Should we store this type of analytics data?
     */
    fun shouldStore(type: EnumAnalyticType): Boolean

    /**
     * Store [data] under [name], as type [type]
     * Returns true if successfully stored; false otherwise
     */
    fun store(data: Any, type: EnumAnalyticType): Boolean

    /**
     * Provide previously stored data of name [name] and type [type]
     * Returns the data stored under the name; null otherwise
     */
    fun provide(name: String, type: EnumAnalyticType, clientInfo: ClientInfo?): DataSource?

    /**
     * Provide previously stored data of name [name] and type [type] to the routing context.
     * Returns true if handled; false otherwise. If false, [provide] is called.
     */
    fun provide(name: String, type: EnumAnalyticType, context: RoutingContext, clientInfo: ClientInfo?): Boolean

    fun isStored(name: String, type: EnumAnalyticType): Boolean
}