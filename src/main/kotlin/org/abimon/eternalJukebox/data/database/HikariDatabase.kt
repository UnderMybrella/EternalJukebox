package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.errPrintln
import java.sql.Connection
import java.sql.ResultSet
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
        var insertStr = ""
            if(databaseType == 0){
                //mysql
                insertStr ="INSERT INTO overrides (id, url) VALUES (?, ?) ON DUPLICATE KEY UPDATE url=VALUES(url);"
            }
            else if(databaseType == 1){
                //sqlserver
                insertStr = "MERGE INTO overrides as o USING (select id=?,url=?) as s on o.id = s.id when matched then update set id=s.id, url=s.url when not matched then insert (id, url) values (id, url);"
            }
            val insert = connection.prepareStatement(insertStr)
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

        if (results.next())
            return@use JukeboxAccount(
                results.getString("eternal_id"),
                results.getString("google_id"),
                results.getString("google_access_token"),
                results.getString("google_refresh_token"),
                results.getString("eternal_access_token")
            )
        return@use null
    }

    override fun provideAccountForGoogleID(googleID: String, clientInfo: ClientInfo?): JukeboxAccount? =
        use { connection ->
            val select = connection.prepareStatement("SELECT * FROM accounts WHERE google_id=?;")
            select.setString(1, googleID)
            select.execute()

            val results = select.resultSet

            if (results.next())
                return@use JukeboxAccount(
                    results.getString("eternal_id"),
                    results.getString("google_id"),
                    results.getString("google_access_token"),
                    results.getString("google_refresh_token"),
                    results.getString("eternal_access_token")
                )
            return@use null
        }

    override fun provideAccountForEternalAuth(eternalAuth: String, clientInfo: ClientInfo?): JukeboxAccount? =
        use { connection ->
            val select = connection.prepareStatement("SELECT * FROM accounts WHERE eternal_access_token=?;")
            select.setString(1, eternalAuth)
            select.execute()

            val results = select.resultSet

            if (results.next())
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

            if (select.resultSet.next()) {
                val update =
                    connection.prepareStatement("UPDATE accounts SET google_access_token=?, google_refresh_token=?, eternal_access_token=? WHERE eternal_id=?;")

                update.setString(1, account.googleAccessToken)
                update.setString(2, account.googleRefreshToken)
                update.setString(3, account.eternalAccessToken)
                update.setString(4, account.eternalID)

                update.execute()
            } else {
                val insert =
                    connection.prepareStatement("INSERT INTO accounts (eternal_id, google_id, google_access_token, google_refresh_token, eternal_access_token) VALUES (?, ?, ?, ?, ?);")

                insert.setString(1, account.eternalID)
                insert.setString(2, account.googleID)
                insert.setString(3, account.googleAccessToken)
                insert.setString(4, account.googleRefreshToken)
                insert.setString(5, account.eternalAccessToken)
                insert.execute()
            }
        }
    }

    override fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo> =
        use { connection ->
        var selectStr = ""
        if(databaseType == 0){
            //mysql
            selectStr = "SELECT song_id, hits FROM popular WHERE service=? ORDER BY hits DESC LIMIT 5"
        }

        else if(databaseType == 1){
            //sqlserver
            selectStr = "SELECT top 5 song_id, hits FROM popular WHERE service=? ORDER BY hits DESC"
        }
            val select = connection.prepareStatement(selectStr)
            select.setString(1, service)
            select.execute()

            val results = select.resultSet
            val popular: MutableList<String> = ArrayList()

            while (results.next()) {
                popular.add(results.getString("song_id"))
            }

            return@use popular
        }.mapNotNull { songID -> getInfo(songID, clientInfo) }

    fun getInfo(songID: String, clientInfo: ClientInfo?): JukeboxInfo? {
        val info = use { connection ->
            var selectStr = ""
            if(databaseType == 0){
                //mysql
            selectStr = "SELECT song_name, song_title, song_artist, song_url, song_duration FROM info_cache WHERE id=? LIMIT 1"
            }
            else if(databaseType == 1){   
                //sqlserver         
            selectStr = "SELECT top 1 song_name, song_title, song_artist, song_url, song_duration FROM info_cache WHERE id=?"
            }
            val select = connection.prepareStatement(selectStr)
            select.setString(1, songID)
            select.execute()

            select.resultSet.use { rs ->
                if (rs.next()) {
                    JukeboxInfo(
                        service = "SPOTIFY",
                        id = songID,
                        name = rs.getString("song_name"),
                        title = rs.getString("song_title"),
                        artist = rs.getString("song_artist"),
                        url = rs.getString("song_url"),
                        duration = rs.getInt("song_duration")
                    )
                } else {
                    null
                }
            }
        }

        if (info != null) return info

        val result = runBlocking { EternalJukebox.spotify.getInfo(songID, clientInfo) } ?: return null

        use { connection ->
            val insert =
                connection.prepareStatement("INSERT INTO info_cache(id, song_name, song_title, song_artist, song_url, song_duration) VALUES (?, ?, ?, ?, ?, ?);")
            insert.setString(1, result.id)
            insert.setString(2, result.name)
            insert.setString(3, result.title)
            insert.setString(4, result.artist)
            insert.setString(5, result.url)
            insert.setInt(6, result.duration)
            insert.execute()
        }

        return result
    }

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
        use { connection ->
            var insertUpdateStr = ""
            if(databaseType == 0){
                //mysql
                insertUpdateStr = "INSERT INTO popular (song_id, service, hits) VALUES(?, ?, 1) ON DUPLICATE KEY UPDATE hits = hits + 1"
            }
            else if(databaseType == 1){
                //sqlserver
                insertUpdateStr = "MERGE INTO popular as p USING (select song_id=?,service=?, hits=1) as s on p.song_id = s.song_id when matched then update set hits=p.hits + 1 when not matched then insert (song_id, service, hits) values(song_id, service, hits);"
            }
            val insertUpdate = connection.prepareStatement(insertUpdateStr)
            insertUpdate.setString(1, id)
            insertUpdate.setString(2, service)
            insertUpdate.execute()

            Unit
        }
    }

    override fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String {
        val joined = buildString {
            for (param in params) {
                if (length > 4096)
                    break

                if (isNotBlank())
                    append('&')

                append(param)
            }
        }

        var id = use { connection ->
            val select = connection.prepareStatement("SELECT * FROM short_urls WHERE params=?;",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
            //BoySanic - I added the TYPE_SCROLL_INSENSITIVE flag here so that the results.first() line would work. I'm not sure if this is database type independent.
            select.setString(1, joined)
            select.execute()

            val results = select.resultSet

            if (results.first()) results.getString("id")
            else null
        }

        if (id != null) return id

        id = obtainNewShortID()

        use { connection ->
            val insert = connection.prepareStatement("INSERT INTO short_urls (id, params) VALUES (?, ?);")
            insert.setString(1, id)
            insert.setString(2, joined)
            insert.execute()
        }

        return id
    }

    override fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? = use { connection ->
        val select = connection.prepareStatement("SELECT * FROM short_urls WHERE id=?;",ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)
        //BoySanic - I added the TYPE_SCROLL_INSENSITIVE flag here so that the results.first() line would work. I'm not sure if this is database type independent.
    
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
        if (resultSet.next()) {
            val path = resultSet.getString("path")

            val drop = connection.prepareStatement("DELETE FROM oauth_state WHERE id=?;")
            drop.setString(1, state)
            drop.execute()

            return@use path
        }
        return@use null
    }

    override fun storeOAuthState(path: String, clientInfo: ClientInfo?): String = use { connection ->
        val id = obtainNewShortID("oauth_state", 32)

        val insert = connection.prepareStatement("INSERT INTO oauth_state (id, path) VALUES (?, ?);")
        insert.setString(1, id)
        insert.setString(2, path)
        insert.execute()

        return@use id
    }

    override fun provideAudioLocation(id: String, clientInfo: ClientInfo?): String? = use location@{ connection ->
        val select = connection.prepareStatement("SELECT location FROM audio_locations WHERE id=?;")
        select.setString(1, id)
        select.execute()

        return@location select.resultSet.use { resultSet ->
            if (resultSet.next())
                return@use resultSet.getString("location")
            return@use null
        }
    }

    override fun storeAudioLocation(id: String, location: String, clientInfo: ClientInfo?) {
        use { connection ->
            var insertStr = ""
            if(databaseType == 0){
                //mysql
                insertStr = "INSERT INTO audio_locations (id, location) VALUES (?, ?) ON DUPLICATE KEY UPDATE location=VALUES(location);"
            }
            else if(databaseType == 1){
                //sqlserver
                insertStr = "MERGE INTO audio_locations as al using (select id=?, location=?) as s on al.id=s.id when matched then update set id=s.id, location=s.location when not matched then insert (id, location) values (id, location);"
            }
            val insert = connection.prepareStatement(insertStr)
            insert.setString(1, id)
            insert.setString(2, location)
            insert.execute()
        }
    }

    fun obtainNewShortID(table: String = "short_urls", count: Int = 5): String {
        for (i in 0 until 4096) {
            val id =
                buildString {
                    repeat(count) {
                        append(
                            EternalJukebox.BASE_64_URL[EternalJukebox.secureRandom.nextInt(
                                64
                            )]
                        )
                    }
                }
            val exists = use { connection ->
                val preparedSelect = connection.prepareStatement("SELECT top 1 id FROM short_urls WHERE id=?")
                preparedSelect.setString(1, id)
                preparedSelect.execute()
                preparedSelect.resultSet.next()
            }

            if (!exists) return id

            println("Generated $id, no success")
        }

        errPrintln("We've run out of new short IDs to send. This is bad.")

        throw IllegalStateException("Run out of IDs")
    }

    open infix fun <T> use(op: (Connection) -> T): T = ds.connection.use(op)
    var databaseType: Int = 0
    fun checkjbdcUrl(jdbcUrl: String) : Int{
        if("sqlserver" in jdbcUrl){
            return 1
        }
        if("mysql" in jdbcUrl){
            return 0
        }
        else{
            throw Error("Database info not populated, or unimplemented database type!")
            return 1024
        }
        //Add more if other sql servers have syntax problems
    }
    fun initialise() {
        use { connection ->
            //            connection.createStatement().execute("USE $databaseName")
        var jdbcUrl = databaseOptions["jdbcUrl"]?.toString() ?: throw IllegalStateException("jdbcUrl was not provided!")
        databaseType = checkjbdcUrl(jdbcUrl)
        if(databaseType == 0){
            //mysql
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS overrides (id VARCHAR(64) PRIMARY KEY NOT NULL, url VARCHAR(8192) NOT NULL);")
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS short_urls (id VARCHAR(16) PRIMARY KEY NOT NULL, params VARCHAR(4096) NOT NULL);")
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS accounts (eternal_id VARCHAR(64) PRIMARY KEY NOT NULL, google_id VARCHAR(64) NOT NULL, google_access_token VARCHAR(255), google_refresh_token VARCHAR(255), eternal_access_token VARCHAR(255));")
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS popular (id INT PRIMARY KEY AUTO_INCREMENT, song_id VARCHAR(64) NOT NULL, service VARCHAR(64) NOT NULL, hits BIGINT NOT NULL);")
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS audio_locations (id VARCHAR(64) PRIMARY KEY NOT NULL, location VARCHAR(8192) NOT NULL);")
            connection.createStatement()
                .execute("CREATE TABLE IF NOT EXISTS info_cache (id VARCHAR(64) PRIMARY KEY NOT NULL, song_name VARCHAR(256) NOT NULL, song_title VARCHAR(256) NOT NULL, song_artist VARCHAR(256) NOT NULL, song_url VARCHAR(1024) NOT NULL, song_duration INT NOT NULL);")

            connection.createStatement().execute("DROP TABLE IF EXISTS oauth_state;")
            connection.createStatement()
                .execute("CREATE TABLE oauth_state (id VARCHAR(32) PRIMARY KEY NOT NULL, path VARCHAR(8192) NOT NULL);")

            Unit
        }
        else if(databaseType == 1){
            //sqlserver
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'overrides') CREATE TABLE overrides (id VARCHAR(64) PRIMARY KEY NOT NULL, url VARCHAR(8000) NOT NULL);")
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'short_urls') CREATE TABLE short_urls (id VARCHAR(16) PRIMARY KEY NOT NULL, params VARCHAR(4096) NOT NULL);")
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'accounts') CREATE TABLE accounts (eternal_id VARCHAR(64) PRIMARY KEY NOT NULL, google_id VARCHAR(64) NOT NULL, google_access_token VARCHAR(255), google_refresh_token VARCHAR(255), eternal_access_token VARCHAR(255));")
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'popular') CREATE TABLE popular (id INT PRIMARY KEY IDENTITY(1,1), song_id VARCHAR(64) NOT NULL, service VARCHAR(64) NOT NULL, hits BIGINT NOT NULL);")
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'audio_locations') CREATE TABLE audio_locations (id VARCHAR(64) PRIMARY KEY NOT NULL, location VARCHAR(MAX) NOT NULL);")
            connection.createStatement()
                .execute("IF NOT EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'info_cache') CREATE TABLE info_cache (id VARCHAR(64) PRIMARY KEY NOT NULL, song_name VARCHAR(256) NOT NULL, song_title VARCHAR(256) NOT NULL, song_artist VARCHAR(256) NOT NULL, song_url VARCHAR(1024) NOT NULL, song_duration INT NOT NULL);")

            connection.createStatement().execute("IF EXISTS (select * from INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'oauth_state') DROP TABLE oauth_state;")
            connection.createStatement()
                .execute("CREATE TABLE oauth_state (id VARCHAR(32) PRIMARY KEY NOT NULL, path VARCHAR(MAX) NOT NULL);")

            Unit
        }
        }
    }
}

