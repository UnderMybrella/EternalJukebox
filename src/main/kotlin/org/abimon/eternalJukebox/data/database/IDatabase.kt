package org.abimon.eternalJukebox.data.database

import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo

interface IDatabase {
    fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String?
    fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?)
    fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount?
    fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount?
    fun storeAccount(clientInfo: ClientInfo?, account: JukeboxAccount)
    fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo>
    fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?)
    fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String
    fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? 
}