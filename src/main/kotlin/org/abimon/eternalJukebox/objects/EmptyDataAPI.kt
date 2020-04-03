package org.abimon.eternalJukebox.objects

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.data.analysis.IAnalyser
import org.abimon.eternalJukebox.data.analytics.IAnalyticsProvider
import org.abimon.eternalJukebox.data.analytics.IAnalyticsStorage
import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.database.IDatabase
import org.abimon.eternalJukebox.data.storage.IStorage
import org.abimon.visi.io.DataSource

object EmptyDataAPI: IAnalyser, IAudioSource, IDatabase, IStorage, IAnalyticsStorage, IAnalyticsProvider {
    override fun shouldStore(type: EnumStorageType): Boolean = false
    override suspend fun search(query: String, clientInfo: ClientInfo?): Array<JukeboxInfo> = emptyArray()
    override suspend fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? = null
    override suspend fun analyse(id: String, clientInfo: ClientInfo?): JukeboxTrack? = null
    override fun store(name: String, type: EnumStorageType, data: DataSource, mimeType: String, clientInfo: ClientInfo?): Boolean = false
    override suspend fun getInfo(id: String, clientInfo: ClientInfo?): JukeboxInfo? = null
    override fun provide(name: String, type: EnumStorageType, clientInfo: ClientInfo?): DataSource? = null
    override fun provide(name: String, type: EnumStorageType, context: RoutingContext, clientInfo: ClientInfo?): Boolean = false
    override fun isStored(name: String, type: EnumStorageType): Boolean = false
    override fun shouldStore(type: EnumAnalyticType<*>): Boolean = false
    override fun <T: Any> store(now: Long, data: T, type: EnumAnalyticType<T>): Boolean = false
    override fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String? = null
    override fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?) {}
    override fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount? = null
    override fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo> = emptyList()
    override fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String = ""
    override fun shouldProvide(type: EnumAnalyticType<*>): Boolean = false
    override fun <T: Any> provide(now: Long, type: EnumAnalyticType<T>): T? = null
    override fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount? = null
    override fun provideAccountForEternalAuth(eternalAuth: String, clientInfo: ClientInfo?): JukeboxAccount? = null
    override fun storeAccount(account: JukeboxAccount, clientInfo: ClientInfo?) {}
    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {}
    override fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? = null
    override fun setupWebAnalytics(router: Router) {}
    override fun storeOAuthState(path: String, clientInfo: ClientInfo?): String = ""
    override fun retrieveOAuthState(state: String, clientInfo: ClientInfo?): String? = null
    override fun provideAudioLocation(id: String, clientInfo: ClientInfo?): String? = null
    override fun storeAudioLocation(id: String, location: String, clientInfo: ClientInfo?) {}
}