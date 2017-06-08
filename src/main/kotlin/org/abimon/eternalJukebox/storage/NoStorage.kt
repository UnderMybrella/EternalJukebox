package org.abimon.eternalJukebox.storage

import org.abimon.eternalJukebox.objects.EnumDataType
import java.io.InputStream

object NoStorage: IStorage {
    override fun isStored(name: String, type: EnumDataType): Boolean = false

    override fun provide(name: String, type: EnumDataType): InputStream? = null

    override fun store(name: String, type: EnumDataType, data: InputStream) {}

    override fun shouldHandle(type: EnumDataType): Boolean = type != EnumDataType.UPLOADED_AUDIO
}