package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.errPrintln
import java.sql.Connection

object H2Database : IDatabase {
    val ds: HikariDataSource

    override fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM overrides WHERE id=?;")
        select.setString(1, id)
        select.execute()

        val results = select.resultSet
        if (results.next())
            return@use results.getString("url")
        return@use null
    }

    override fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?) {
        use { connection ->
            val insert = connection.prepareStatement("INSERT INTO overrides (id, url) VALUES (?, ?) ON DUPLICATE KEY UPDATE url=VALUES(url);")
            insert.setString(1, id)
            insert.setString(2, newURL)

            insert.execute()
        }
    }

    override fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount? {
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

    override fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String = use { connection ->
        val joined = buildString {
            for (param in params) {
                if (length > 4096)
                    break

                if (isNotBlank())
                    append('&')

                append(param)
            }
        }

        val select = connection.prepareStatement("SELECT * FROM short_urls WHERE params=?;")
        select.setString(1, joined)
        select.execute()

        val results = select.resultSet

        if (results.first())
            return@use results.getString("id")

        val id = obtainNewShortID(connection)

        val insert = connection.prepareStatement("INSERT INTO short_urls (id, params) VALUES (?, ?);")
        insert.setString(1, id)
        insert.setString(2, joined)
        insert.execute()

        return@use id
    }

    override fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM short_urls WHERE id=?;")
        select.setString(1, id)
        select.execute()

        val results = select.resultSet
        if(results.first())
            return@use results.getString("params").split("&").toTypedArray()

        return@use null
    }

    fun obtainNewShortID(connection: Connection): String {
        val preparedSelect = connection.prepareStatement("SELECT * FROM short_urls WHERE id=?")
        val range = (0 until 4)
        (0 until 4096).forEach {
            val id = buildString { for (i in range) append(EternalJukebox.BASE_64_URL[EternalJukebox.secureRandom.nextInt(64)]) }
            preparedSelect.setString(1, id)
            preparedSelect.execute()
            preparedSelect.resultSet.use {
                if (!it.isBeforeFirst)
                    return@obtainNewShortID id
            }

            println("Generated $id, no success")
        }

        errPrintln("We've run out of new short IDs to send. This is bad.")

        throw IllegalStateException("Run out of IDs")
    }

    init {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:h2:./eternal_jukebox"

        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

        ds = HikariDataSource(config)

        use { connection ->
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS overrides (id VARCHAR(64) PRIMARY KEY NOT NULL, url VARCHAR(8192) NOT NULL);")
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS short_urls (id VARCHAR(16) PRIMARY KEY NOT NULL, params VARCHAR(4096) NOT NULL);")
        }
    }

    infix fun <T> use(op: (Connection) -> T): T = ds.connection.use(op)
}