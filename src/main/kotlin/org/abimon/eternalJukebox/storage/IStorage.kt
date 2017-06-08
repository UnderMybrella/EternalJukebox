package org.abimon.eternalJukebox.storage

import org.abimon.eternalJukebox.objects.EnumDataType
import java.io.InputStream

interface IStorage {
    /**
     * Check if there is data with this name in this storage location.
     * @throws UnsupportedOperationException if this storage method should not deal with this data type
     */
    fun isStored(name: String, type: EnumDataType): Boolean

    /**
     * Provide data from this storage location. You are responsible for closing the stream.
     * @throws UnsupportedOperationException if this storage method should not deal with this data type
     */
    fun provide(name: String, type: EnumDataType): InputStream?

    /**
     * Provide a URL that the data for the type and name can be retrieved from, or null if there isn't one.
     * This should only be called in cases where the data is strictly read and then written, and therefore a redirection is possible
     * @throws UnsupportedOperationException if this storage method should not deal with this data type
     */
    fun provideURL(name: String, type: EnumDataType): String? = null

    /**
     * Store data in this storage location. Closes the input stream after.
     * @throws UnsupportedOperationException if this storage method should not deal with this data type
     */
    fun store(name: String, type: EnumDataType, data: InputStream)

    fun shouldHandle(type: EnumDataType): Boolean
}