package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariDataSource
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.errPrintln
import java.sql.Connection

abstract class HikariDatabase : IDatabase {
    abstract val ds: HikariDataSource

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

    override fun provideAccountForID(accountID: String, clientInfo: ClientInfo?): JukeboxAccount? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM accounts WHERE eternal_id=?;")
        select.setString(1, accountID)
        select.execute()

        val results = select.resultSet

        if(results.next())
            return@use JukeboxAccount(
                    results.getString("eternal_id"),
                    results.getString("google_id"),
                    results.getString("google_access_token"),
                    results.getString("google_refresh_token"),
                    results.getString("eternal_access_token")
            )
        return@use null
    }

    override fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM accounts WHERE google_id=?;")
        select.setString(1, googleID)
        select.execute()

        val results = select.resultSet

        if(results.next())
            return@use JukeboxAccount(
                    results.getString("eternal_id"),
                    results.getString("google_id"),
                    results.getString("google_access_token"),
                    results.getString("google_refresh_token"),
                    results.getString("eternal_access_token")
            )
        return@use null
    }

    override fun provideAccountForEternalAuth(eternalAuth: String, clientInfo: ClientInfo?): JukeboxAccount? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM accounts WHERE eternal_access_token=?;")
        select.setString(1, eternalAuth)
        select.execute()

        val results = select.resultSet

        if(results.next())
            return@use JukeboxAccount(
                    results.getString("eternal_id"),
                    results.getString("google_id"),
                    results.getString("google_access_token"),
                    results.getString("google_refresh_token"),
                    results.getString("eternal_access_token")
            )
        return@use null
    }

    override fun storeAccount(account: JukeboxAccount, clientInfo: ClientInfo?) {
        use { connection ->
            val select = connection.prepareStatement("SELECT eternal_id FROM accounts WHERE eternal_id=?;")
            select.setString(1, account.eternalID)
            select.execute()

            if(select.resultSet.next()) {
                val update = connection.prepareStatement("UPDATE accounts SET google_access_token=?, google_refresh_token=?, eternal_access_token=?;")

                update.setString(1, account.googleAccessToken)
                update.setString(2, account.googleRefreshToken)
                update.setString(3, account.eternalAccessToken)
                update.execute()
            } else {
                val insert = connection.prepareStatement("INSERT INTO accounts (eternal_id, google_id, google_access_token, google_refresh_token, eternal_access_token) VALUES (?, ?, ?, ?, ?);")

                insert.setString(1, account.eternalID)
                insert.setString(2, account.googleID)
                insert.setString(3, account.googleAccessToken)
                insert.setString(4, account.googleRefreshToken)
                insert.setString(5, account.eternalAccessToken)
                insert.execute()
            }
        }
    }

    override fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo> = use { connection ->
        val select = connection.prepareStatement("SELECT song_id, hits FROM popular WHERE service=? ORDER BY hits DESC LIMIT $count;")
        select.setString(1, service)
        select.execute()

        val results = select.resultSet
        val popular: MutableList<JukeboxInfo> = ArrayList()

        while (results.next()) {
            val songID = results.getString("song_id")
            println("$songID: ${results.getLong("hits")}")
            popular.add(EternalJukebox.spotify.getInfo(songID, clientInfo) ?: continue)
        }

        return@use popular
    }

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
        use { connection ->
            val select = connection.prepareStatement("SELECT hits FROM popular WHERE service=? AND song_id=?;")
            select.setString(1, service)
            select.setString(2, id)
            select.execute()

            val results = select.resultSet
            if(results.next()) {
                val update = connection.prepareStatement("UPDATE popular SET hits=? WHERE service=? AND song_id=?;")
                update.setLong(1, results.getLong("hits") + 1)
                update.setString(2, service)
                update.setString(3, id)

                update.execute()
            } else {
                val insert = connection.prepareStatement("INSERT INTO popular (song_id, service, hits) VALUES (?, ?, ?);")
                insert.setString(1, id)
                insert.setString(2, service)
                insert.setLong(3, 1)

                insert.execute()
            }

            Unit
        }
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
        if (results.first())
            return@use results.getString("params").split("&").toTypedArray()

        return@use null
    }

    override fun retrieveOAuthState(state: String, clientInfo: ClientInfo?): String? = use { connection ->
        val select = connection.prepareStatement("SELECT path FROM oauth_state WHERE id=?;")
        select.setString(1, state)
        select.execute()

        val resultSet = select.resultSet
        if(resultSet.next()) {
            val path = resultSet.getString("path")

            val drop = connection.prepareStatement("DELETE FROM oauth_state WHERE id=?;")
            drop.setString(1, state)
            drop.execute()

            return@use path
        }
        return@use null
    }

    override fun storeOAuthState(path: String, clientInfo: ClientInfo?): String = use { connection ->
        val id = obtainNewShortID(connection, "oauth_state", (0 until 32))

        val insert = connection.prepareStatement("INSERT INTO oauth_state (id, path) VALUES (?, ?);")
        insert.setString(1, id)
        insert.setString(2, path)
        insert.execute()

        return@use id
    }

    fun obtainNewShortID(connection: Connection, table: String = "short_urls", range: IntRange = (0 until 4)): String {
        val preparedSelect = connection.prepareStatement("SELECT * FROM $table WHERE id=?")
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

    open infix fun <T> use(op: (Connection) -> T): T = ds.connection.use(op)

    fun initialise() {
        use { connection ->
            //            connection.createStatement().execute("USE $databaseName")

            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS overrides (id VARCHAR(64) PRIMARY KEY NOT NULL, url VARCHAR(8192) NOT NULL);")
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS short_urls (id VARCHAR(16) PRIMARY KEY NOT NULL, params VARCHAR(4096) NOT NULL);")
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS accounts (eternal_id VARCHAR(64) PRIMARY KEY NOT NULL, google_id VARCHAR(64) NOT NULL, google_access_token VARCHAR(255), google_refresh_token VARCHAR(255), eternal_access_token VARCHAR(255));")
            connection.createStatement().execute("CREATE TABLE IF NOT EXISTS popular (id INT PRIMARY KEY AUTO_INCREMENT, song_id VARCHAR(64) NOT NULL, service VARCHAR(64) NOT NULL, hits BIGINT NOT NULL)")

            connection.createStatement().execute("DROP TABLE IF EXISTS oauth_state;")
            connection.createStatement().execute("CREATE TABLE oauth_state (id VARCHAR(32) PRIMARY KEY NOT NULL, path VARCHAR(8192) NOT NULL);")

            Unit
        }
    }
}