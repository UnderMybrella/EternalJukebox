package org.abimon.eternalJukebox.data.storage

import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.visi.io.DataSource

interface IStorage {
    /**
     * Should we store this type of storage?
     */
    fun shouldStore(type: EnumStorageType)

    /**
     * Store [data] under [name], as type [type]
     * Returns true if successfully stored; false otherwise
     */
    fun store(name: String, type: EnumStorageType, data: DataSource): Boolean

    /**
     * Provide previously stored data of name [name] and type [type]
     * Returns the data stored under the name; null otherwise
     */
    fun provide(name: String, type: EnumStorageType): DataSource?

    /**
     * Provide previously stored data of name [name] and type [type] to the routing context.
     * Returns true if handled; false otherwise. If false, [provide] is called.
     */
    fun provide(name: String, type: EnumStorageType, context: RoutingContext): Boolean
}