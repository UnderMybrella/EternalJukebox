package org.abimon.eternalJukebox.storage

import org.abimon.eternalJukebox.objects.EnumDataType
import org.abimon.visi.io.writeTo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object LocalStorage: IStorage {
    val infoDir = File("info")
    val songsDir = File("songs")
    val audioDir = File("audio")
    val logDir = File("logs")
    val profileDir = File("profiles")

    override fun isStored(name: String, type: EnumDataType): Boolean = fileFor(name, type).exists()

    override fun provide(name: String, type: EnumDataType): InputStream? = provide(fileFor(name, type))

    override fun store(name: String, type: EnumDataType, data: InputStream) {
        FileOutputStream(fileFor(name, type)).use { fos -> data.writeTo(fos, closeAfter = true) }
    }

    override fun shouldHandle(type: EnumDataType): Boolean = true

    private fun provide(file: File): FileInputStream? = if(file.exists()) FileInputStream(file) else null
    private fun fileFor(name: String, type: EnumDataType): File {
        when(type) {
            EnumDataType.INFO -> return File(infoDir, name)
            EnumDataType.AUDIO -> return File(songsDir, name)
            EnumDataType.EXT_AUDIO -> return File(audioDir, name)
            EnumDataType.LOG -> return File(logDir, name)
            EnumDataType.PROFILE -> return File(profileDir, name)
            else -> TODO("Storage for $type isn't implemented")
        }
    }

    init {
        if (!infoDir.exists())
            infoDir.mkdir()
        if (!songsDir.exists())
            songsDir.mkdir()
        if (!audioDir.exists())
            audioDir.mkdir()
        if (!logDir.exists())
            logDir.mkdir()
        if (!profileDir.exists())
            profileDir.mkdir()
    }
}