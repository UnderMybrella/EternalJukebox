package org.abimon.eternalJukebox.data.database

import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo
import java.io.File
import java.io.PrintStream
import java.util.*

/**
 * This is not efficient and should not be used for big cases
 */
object FlatFileDatabase : IDatabase {
    val AUDIO_TRACK_OVERRIDE_DB = File(".AUDIO_TRACK_OVERRIDES")

    override fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String? {
        val trackEntry = AUDIO_TRACK_OVERRIDE_DB getEntryForPrimaryKey id ?: return null

        return trackEntry[1]
    }

    override fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?) {
        AUDIO_TRACK_OVERRIDE_DB updateEntry arrayOf(id, newURL)
    }

    override fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun storeAccount(clientInfo: ClientInfo?, account: JukeboxAccount) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    infix fun File.getEntryForPrimaryKey(primaryKey: String): Array<String>? {
        return reader().useAndFilterLine { line -> line.startsWith("$primaryKey|") }
                ?.split('|')
                ?.map { string -> String(Base64.getDecoder().decode(string), Charsets.UTF_8) }
                ?.toTypedArray()
    }

    fun File.getEntryForKey(key: String, index: Int): Array<String>? {
        return reader().useAndFilterLine { line -> line.split('|')[index] == key }
                ?.split('|')
                ?.map { string -> String(Base64.getDecoder().decode(string), Charsets.UTF_8) }
                ?.toTypedArray()
    }

    infix fun File.updateEntry(entry: Array<String>) {
        val primaryKey = entry[0]
        val tmp = File(UUID.randomUUID().toString())
        val out = PrintStream(tmp)

        reader().useLineByLine { line ->
            if (line.startsWith("$primaryKey|"))
                out.println(entry.joinToString("|") { column -> Base64.getEncoder().encodeToString(column.toByteArray(Charsets.UTF_8)) })
            else
                out.println(line)
        }

        this.delete()
        tmp.renameTo(this)
    }
}