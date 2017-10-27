package org.abimon.eternalJukebox.objects

import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.database.IDatabase
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.visi.io.DataSource

object EmptyDataAPI: IAnalyser, IAudioSource, IDatabase, IStorage {
    override fun shouldStore(type: EnumStorageType): Boolean = false
    override fun search(query: String, clientInfo: ClientInfo?): Array<JukeboxInfo> = emptyArray()
    override fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? = null
    override fun analyse(id: String, clientInfo: ClientInfo?): JukeboxTrack? = null
    override fun store(name: String, type: EnumStorageType, data: DataSource, clientInfo: ClientInfo?): Boolean = false
    override fun getInfo(id: String, clientInfo: ClientInfo?): JukeboxInfo? = null
    override fun provide(name: String, type: EnumStorageType, clientInfo: ClientInfo?): DataSource? = null
    override fun provide(name: String, type: EnumStorageType, context: RoutingContext, clientInfo: ClientInfo?): Boolean = false
    override fun isStored(name: String, type: EnumStorageType): Boolean = false
}