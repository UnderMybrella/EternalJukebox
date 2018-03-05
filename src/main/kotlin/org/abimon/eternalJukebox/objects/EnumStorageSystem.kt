package org.abimon.eternalJukebox.objects

import org.abimon.eternalJukebox.data.storage.GoogleStorage
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.eternalJukebox.data.storage.LocalStorage
import kotlin.reflect.KClass

enum class EnumStorageSystem(val klass: KClass<out IStorage>) {
    LOCAL(LocalStorage::class),
    GOOGLE(GoogleStorage::class);

    val storage: IStorage
        get() = klass.objectInstance!!
}