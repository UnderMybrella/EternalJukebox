package org.abimon.eternalJukebox.objects

import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.database.IDatabase
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.visi.io.DataSource

object EmptyDataAPI: IAnalyser, IAudioSource, IDatabase, IStorage {
    override fun shouldStore(type: EnumStorageType): Boolean = false
    override fun search(query: String): Array<JukeboxInfo> = emptyArray()
    override fun provide(info: JukeboxInfo): DataSource? = null
    override fun analyse(id: String): JukeboxTrack? = null
    override fun store(name: String, type: EnumStorageType, data: DataSource): Boolean = false
    override fun getInfo(id: String): JukeboxInfo? = null
    override fun provide(name: String, type: EnumStorageType): DataSource? = null
    override fun provide(name: String, type: EnumStorageType, context: RoutingContext): Boolean = false
    override fun isStored(name: String, type: EnumStorageType): Boolean = false
}