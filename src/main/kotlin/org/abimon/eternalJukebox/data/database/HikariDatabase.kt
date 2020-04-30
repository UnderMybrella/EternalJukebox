package org.abimon.eternalJukebox.data.database

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxAccount
import org.abimon.eternalJukebox.objects.JukeboxInfo
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLTransientConnectionException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class HikariDatabase : IDatabase {
    abstract val ds: HikariDataSource
    val TIME_BETWEEN_UPDATES_MS = EternalJukebox.config.hikariBatchTimeBetweenUpdatesMs
    val SHORT_ID_UPDATE_TIME_MS = EternalJukebox.config.hikariBatchShortIDUpdateTimeMs

    val popularLocks: Map<String, ReentrantReadWriteLock> =
        mapOf("jukebox" to ReentrantReadWriteLock(), "canonizer" to ReentrantReadWriteLock())
    val popularUpdates: Map<String, Channel<String>> =
        mapOf("jukebox" to Channel(Channel.BUFFERED), "canonizer" to Channel(Channel.BUFFERED))
    val popularSongs: MutableMap<String, Array<String>> =
        mutableMapOf("jukebox" to emptyArray(), "canonizer" to emptyArray())
    val locationUpdates: Channel<Pair<String, String>> =
        Channel(Channel.BUFFERED)
    val shortIDUpdates: Channel<Pair<Long, String>> =
        Channel(Channel.BUFFERED)
    val infoUpdates: Channel<JukeboxInfo> =
        Channel(Channel.BUFFERED)

    val infoCache: AsyncCache<String, JukeboxInfo> = Caffeine.newBuilder()
        .expireAfterAccess(EternalJukebox.config.jukeboxInfoCacheStayDurationMinutes.toLong(), TimeUnit.MINUTES)
        .maximumSize(EternalJukebox.config.maximumJukeboxInfoCacheSize)
        .buildAsync()

    val shortIDCache: AsyncCache<Long, String> = Caffeine.newBuilder()
        .expireAfterAccess(EternalJukebox.config.shortIDCacheStayDurationMinutes.toLong(), TimeUnit.MINUTES)
        .maximumSize(EternalJukebox.config.maximumShortIDCacheSize)
        .buildAsync()

    val shortIDReverseCache: AsyncCache<String, Long> = Caffeine.newBuilder()
        .expireAfterAccess(EternalJukebox.config.shortIDCacheStayDurationMinutes.toLong(), TimeUnit.MINUTES)
        .maximumSize(EternalJukebox.config.maximumShortIDCacheSize)
        .buildAsync()

    val overridesCache: AsyncCache<String, String> = Caffeine.newBuilder()
        .expireAfterAccess(EternalJukebox.config.overridesCacheStayDurationMinutes.toLong(), TimeUnit.MINUTES)
        .maximumSize(EternalJukebox.config.maximumOverridesCacheSize)
        .buildAsync()

    val locationCache: AsyncCache<String, String> = Caffeine.newBuilder()
        .expireAfterAccess(EternalJukebox.config.locationsCacheStayDurationMinutes.toLong(), TimeUnit.MINUTES)
        .maximumSize(EternalJukebox.config.maximumLocationCacheSize)
        .buildAsync()

    val shortIDStorm = LocalisedSnowstorm.getInstance(1585659600000L)

    val dispatcher = newSingleThreadContext("HikariPropagateDispatcher")

    override suspend fun provideAudioTrackOverride(id: String, clientInfo: ClientInfo?): String? {
        val cachedValue = overridesCache.get(id) { id ->
            use { connection ->
                val select = connection.prepareStatement("SELECT * FROM overrides WHERE id=?;")
                select.setString(1, id)
                select.execute()

                val results = select.resultSet
                if (results.next())
                    return@use results.getString("url")
                return@use null
            }
        }

        return cachedValue.await()
    }

    override fun storeAudioTrackOverride(id: String, newURL: String, clientInfo: ClientInfo?) {
        use { connection ->
            val insert =
                connection.prepareStatement("INSERT INTO overrides (id, url) VALUES (?, ?) ON DUPLICATE KEY UPDATE url=VALUES(url);")
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

    override suspend fun providePopularSongs(service: String, count: Int, clientInfo: ClientInfo?): List<JukeboxInfo> {
//        use { connection ->
//            val select =
//                connection.prepareStatement("SELECT song_id, hits FROM popular WHERE service=? ORDER BY hits DESC LIMIT $count;")
//            select.setString(1, service)
//            select.execute()
//
//            val results = select.resultSet
//            val popular: MutableList<String> = ArrayList()
//
//            while (results.next()) {
//                popular.add(results.getString("song_id"))
//            }
//
//            return@use popular
//        }.mapNotNull { songID -> getInfo(songID, clientInfo) }

        return popularLocks[service]?.read {
            popularSongs[service]?.take(count)
        }?.mapNotNull { songID ->
            getInfo(songID, clientInfo)
        } ?: emptyList()
    }

    private suspend fun getInfo(songID: String, clientInfo: ClientInfo?): JukeboxInfo? {
        val cachedValue = infoCache.get(songID) { _, executor ->
            GlobalScope.future(executor.asCoroutineDispatcher()) {
                val info = try {
                    use { connection ->
                        val select =
                            connection.prepareStatement("SELECT song_name, song_title, song_artist, song_url, song_duration FROM info_cache WHERE id=? LIMIT 1;")
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
                } catch (sqlTransientException: SQLTransientConnectionException) {
                    logger.warn("Could not obtain connection in time, going to Spotify: ", sqlTransientException)
                    null
                }

                if (info != null) return@future info
                val result = EternalJukebox.spotify.getInfo(songID, clientInfo) ?: return@future null

//                use { connection ->
//                    val insert =
//                        connection.prepareStatement("INSERT INTO info_cache(id, song_name, song_title, song_artist, song_url, song_duration) VALUES (?, ?, ?, ?, ?, ?);")
//                    insert.setString(1, result.id)
//                    insert.setString(2, result.name)
//                    insert.setString(3, result.title)
//                    insert.setString(4, result.artist)
//                    insert.setString(5, result.url)
//                    insert.setInt(6, result.duration)
//                    insert.execute()
//                }

                infoUpdates.send(result)

                return@future result
            }
        }

        return cachedValue.await()
    }

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
//        use { connection ->
//            val insertUpdate =
//                connection.prepareStatement("INSERT INTO popular (song_id, service, hits) VALUES(?, ?, 1) ON DUPLICATE KEY UPDATE hits = hits + 1")
//
//            insertUpdate.setString(1, id)
//            insertUpdate.setString(2, service)
//            insertUpdate.execute()
//
//            Unit
//        }

        GlobalScope.launch(dispatcher) { popularUpdates[service]?.send(id) }
    }

    override suspend fun provideShortURL(params: Array<String>, clientInfo: ClientInfo?): String {
        val joined = buildString {
            for (param in params) {
                if (length > 4096)
                    break

                if (isNotBlank())
                    append('&')

                append(param)
            }
        }

        var id = shortIDReverseCache.get(joined) { _ ->
            use { connection ->
                val select = connection.prepareStatement("SELECT id FROM short_urls WHERE params=?;")
                select.setString(1, joined)
                select.execute()

                val results = select.resultSet

                if (results.first()) results.getString("id").toBase64Long()
                else null
            }
        }.await()

        if (id != null) return id.toBase64()

        id = obtainNewShortID()

//        withContext(Dispatchers.IO) {
//            use { connection ->
//                val insert = connection.prepareStatement("INSERT INTO short_urls (id, params) VALUES (?, ?);")
//                insert.setString(1, id.toBase64())
//                insert.setString(2, joined)
//                insert.execute()
//            }
//        }

        shortIDCache.put(id, CompletableFuture.completedFuture(joined))
        shortIDReverseCache.put(joined, CompletableFuture.completedFuture(id))
        shortIDUpdates.send(Pair(id, joined))

        return id.toBase64()
    }

    override suspend fun expandShortURL(id: String, clientInfo: ClientInfo?): Array<String>? {
        val expanded = shortIDCache.get(id.toBase64LongOrNull() ?: return null) { _ ->
            use { connection ->
                val select = connection.prepareStatement("SELECT params FROM short_urls WHERE id=?;")
                select.setString(1, id)
                select.execute()

                select.resultSet.use { rs ->
                    if (rs.next()) {
                        rs.getString("params")
                    } else {
                        null
                    }
                }
            }
        }

        return expanded.await()?.split("&")?.toTypedArray()
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

    override suspend fun storeOAuthState(path: String, clientInfo: ClientInfo?): String = use { connection ->
        val id = obtainNewShortID().toBase64()

        val insert = connection.prepareStatement("INSERT INTO oauth_state (id, path) VALUES (?, ?);")
        insert.setString(1, id)
        insert.setString(2, path)
        insert.execute()

        return@use id
    }

    override suspend fun provideAudioLocation(id: String, clientInfo: ClientInfo?): String? {
        val location = locationCache.get(id) { _ ->
            use { connection ->
                val select = connection.prepareStatement("SELECT location FROM audio_locations WHERE id=?;")
                select.setString(1, id)
                select.execute()

                select.resultSet.use { resultSet ->
                    resultSet.takeIf(ResultSet::next)?.getString("location")
                }
            }
        }

        return location.await()
    }

    override suspend fun storeAudioLocation(id: String, location: String, clientInfo: ClientInfo?) {
        locationCache.put(id, CompletableFuture.completedFuture(location))
        locationUpdates.send(Pair(id, location))

        /**
         * use { connection ->
        val insert =
        connection.prepareStatement("INSERT INTO audio_locations (id, location) VALUES (?, ?) ON DUPLICATE KEY UPDATE location=VALUES(location);")
        insert.setString(1, id)
        insert.setString(2, location)

        insert.execute()
        }
         */
    }

    suspend fun obtainNewShortID(): Long {
//        for (i in 0 until 4096) {
//            val id =
//                buildString {
//                    repeat(count) {
//                        append(
//                            EternalJukebox.BASE_64_URL[EternalJukebox.secureRandom.nextInt(
//                                64
//                            )]
//                        )
//                    }
//                }
//            val exists = use { connection ->
//                val preparedSelect = connection.prepareStatement("SELECT id FROM $table WHERE id=? LIMIT 1")
//                preparedSelect.setString(1, id)
//                preparedSelect.execute()
//                preparedSelect.resultSet.next()
//            }
//
//            if (!exists) return id
//
//            println("Generated $id, no success")
//        }
//
//        errPrintln("We've run out of new short IDs to send. This is bad.")
//
//        throw IllegalStateException("Run out of IDs")

        return shortIDStorm.generateLongId()
    }

    inline infix fun <T> use(op: (Connection) -> T): T = ds.connection.use(op)

    open fun updatePopular(connection: Connection, updates: Map<String, Int>) {
        val insertUpdate =
            connection.prepareStatement("INSERT INTO popular (song_id, service, hits) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE hits = hits + ?")

        updates.entries.chunked(100) { chunk ->
            insertUpdate.clearBatch()

            chunk.forEach { (key, amount) ->
                insertUpdate.setString(1, key.substringBefore(':'))
                insertUpdate.setString(2, key.substringAfter(':'))
                insertUpdate.setInt(3, amount)
                insertUpdate.setInt(4, amount)
                insertUpdate.addBatch()
            }

            insertUpdate.executeBatch()
        }
    }

    open fun updateLocation(connection: Connection, updates: Map<String, String>) {
        val insert =
            connection.prepareStatement("INSERT INTO audio_locations (id, location) VALUES (?, ?) ON DUPLICATE KEY UPDATE location=VALUES(location);")
        updates.entries.chunked(100) { chunk ->
            insert.clearBatch()

            chunk.forEach { (id, location) ->
                insert.setString(1, id)
                insert.setString(2, location)
                insert.addBatch()
            }

            insert.executeBatch()
        }
    }

    open fun updateInfo(connection: Connection, updates: Collection<JukeboxInfo>) {
        val insert =
            connection.prepareStatement("INSERT INTO info_cache(id, song_name, song_title, song_artist, song_url, song_duration) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE;")

        updates.chunked(100) { chunk ->
            insert.clearBatch()

            chunk.forEach { info ->
                insert.setString(1, info.id)
                insert.setString(2, info.name)
                insert.setString(3, info.title)
                insert.setString(4, info.artist)
                insert.setString(5, info.url)
                insert.setInt(6, info.duration)
                insert.addBatch()
            }

            insert.executeBatch()
        }
    }

    fun initialise() {
        use { connection ->
            //            connection.createStatement().execute("USE $databaseName")

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

        GlobalScope.launch {
            while (isActive) {
                println("[Condensing Popular Updates]")
                val updates: MutableMap<String, Int> = HashMap()

                withTimeoutOrNull(5_000) {
                    popularUpdates.forEach { (service, channel) ->
                        while (!channel.isEmpty)
                            updates.compute("$service:${channel.receive()}") { _, v -> v?.plus(1) ?: 1 }
                    }
                }

                println("[Updating Popular Database]")

                withContext(Dispatchers.IO) {
                    use { connection ->
                        updatePopular(connection, updates)

                        val select =
                            connection.prepareStatement("SELECT song_id, hits FROM popular WHERE service=? ORDER BY hits DESC LIMIT 100;")

                        popularSongs.keys.forEach { service ->
                            select.setString(1, service)
                            select.execute()

                            val results = select.resultSet
                            val popular: MutableList<String> = ArrayList()

                            while (results.next()) {
                                popular.add(results.getString("song_id"))
                            }

                            popularLocks[service]?.write {
                                popularSongs[service] = popular.toTypedArray()
                            }
                        }
                    }
                }

                delay(TIME_BETWEEN_UPDATES_MS)
            }
        }

        GlobalScope.launch {
            while (isActive) {
                println("[Condensing Location Updates]")
                val updates: MutableMap<String, String> = HashMap()

                withTimeoutOrNull(5_000) {
                    while (!locationUpdates.isEmpty) {
                        val (k, v) = locationUpdates.receive()
                        updates[k] = v
                    }
                }

                println("[Updating Location Database]")

                withContext(Dispatchers.IO) {
                    use { connection ->
                        updateLocation(connection, updates)
                    }
                }

                delay(TIME_BETWEEN_UPDATES_MS)
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            use { connection ->
                val updates: MutableMap<String, String> = HashMap()
                val insert = connection.prepareStatement("INSERT INTO short_urls (id, params) VALUES (?, ?);")

                while (isActive) {
                    updates.clear()
                    withTimeoutOrNull(SHORT_ID_UPDATE_TIME_MS) {
                        while (!shortIDUpdates.isEmpty) {
                            val (k, v) = shortIDUpdates.receive()
                            updates[k.toBase64()] = v
                        }
                    }

                    updates.entries.chunked(100) { chunk ->
                        insert.clearBatch()

                        chunk.forEach { (id, params) ->
                            insert.setString(1, id)
                            insert.setString(2, params)
                            insert.addBatch()
                        }

                        insert.executeBatch()
                    }
                    
                    delay(SHORT_ID_UPDATE_TIME_MS)
                }
            }
        }

        GlobalScope.launch {
            while (isActive) {
                println("[Condensing Info Updates]")
                val updates: MutableMap<String, JukeboxInfo> = HashMap()

                withTimeoutOrNull(5_000) {
                    while (!infoUpdates.isEmpty) {
                        val info = infoUpdates.receive()
                        updates[info.id] = info
                    }
                }

                println("[Updating Info Database]")

                withContext(Dispatchers.IO) {
                    use { connection ->
                        updateInfo(connection, updates.values)
                    }
                }

                delay(TIME_BETWEEN_UPDATES_MS)
            }
        }
    }
}
