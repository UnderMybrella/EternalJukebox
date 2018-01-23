package org.abimon.eternalJukebox.objects

import org.abimon.eternalJukebox.data.database.H2Database
import org.abimon.eternalJukebox.data.database.IDatabase
import kotlin.reflect.KClass

enum class EnumDatabaseType(val klass: KClass<out IDatabase>) {
    H2(H2Database::class)
}