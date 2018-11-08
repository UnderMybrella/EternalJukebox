package org.abimon.eternalJukebox.data.database

import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo

interface IDatabase {
    val databaseOptions
        get() = EternalJukebox.config.databaseOptions
    val databaseName
        get() = databaseOptions["databaseName"] ?: "eternal_jukebox"

    fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String?
    fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?)

    fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount?
    fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount?
    fun provideAccountForEternalAuth(eternalAuth: String, clientInfo: ClientInfo?): JukeboxAccount?
    fun storeAccount(account: JukeboxAccount, clientInfo: ClientInfo?)

    fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo>
    fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?)

    fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String
    fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>?

    fun provideAudioLocation(id: String, clientInfo: ClientInfo?): String?
    fun storeAudioLocation(id: String, location: String, clientInfo: ClientInfo?)

    fun storeOAuthState(path: String, clientInfo: ClientInfo?): String
    fun retrieveOAuthState(state: String, clientInfo: ClientInfo?): String?
}