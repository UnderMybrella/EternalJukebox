package org.abimon.eternalJukebox.storage

import org.abimon.eternalJukebox.objects.EnumDataType
import org.abimon.visi.io.readAllBytes
import java.io.ByteArrayInputStream
import java.io.InputStream

object NoStorage: IStorage {
    var buffer: ByteArray? = null

    override fun isStored(name: String, type: EnumDataType): Boolean = false

    override fun provide(name: String, type: EnumDataType): InputStream? = if(buffer == null) null else ByteArrayInputStream(buffer)

    override fun store(name: String, type: EnumDataType, data: InputStream) = data.use { inputStream -> buffer = inputStream.readAllBytes() }

    override fun shouldHandle(type: EnumDataType): Boolean = type != EnumDataType.UPLOADED_AUDIO
}