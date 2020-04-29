package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import java.sql.Connection

object H2Database : HikariDatabase() {
    override val ds: HikariDataSource

    override fun updatePopular(connection: Connection, updates: Map<String, Int>) {
        val select =
            connection.prepareStatement("SELECT id FROM popular WHERE service=? AND song_id=? LIMIT 1;")
        val update = connection.prepareStatement("UPDATE popular SET hits=hits + ? WHERE id=?;")
        val insert =
            connection.prepareStatement("INSERT INTO popular (song_id, service, hits) VALUES (?, ?, ?);")

        updates.entries.chunked(100) { chunk ->
            insert.clearBatch()
            update.clearBatch()
            update.clearParameters()

            chunk.forEach { (key, amount) ->
                select.setString(1, key.substringBefore(':'))
                select.setString(2, key.substringAfter(':'))
                select.execute()

                select.resultSet.use { rs ->
                    if (rs.next()) {
                        update.setInt(1, amount)
                        update.setLong(2, rs.getLong("id"))
                        update.addBatch()
                    } else {
                        insert.setString(1, key.substringAfter(':'))
                        insert.setString(2, key.substringBefore(':'))
                        insert.setInt(3, amount)
                        insert.addBatch()
                    }
                }
            }

            insert.executeBatch()
            update.executeBatch()
        }
    }

    override fun updateLocation(connection: Connection, updates: Map<String, String>) {
        val select =
            connection.prepareStatement("SELECT id FROM audio_locations WHERE id=?;")
        val update = connection.prepareStatement("UPDATE audio_locations SET location=? WHERE id=?;")
        val insert =
            connection.prepareStatement("INSERT INTO audio_locations (id,location) VALUES (?, ?);")

        updates.entries.chunked(100) { chunk ->
            insert.clearBatch()
            update.clearBatch()
            update.clearParameters()

            chunk.forEach { (songID, location) ->
                select.setString(1, songID)
                select.execute()

                select.resultSet.use { rs ->
                    if (rs.next()) {
                        update.setString(1, location)
                        update.setString(2, songID)
                        update.addBatch()
                    } else {
                        insert.setString(1, songID)
                        insert.setString(3, location)
                        insert.addBatch()
                    }
                }
            }

            insert.executeBatch()
            update.executeBatch()
        }
    }

    override fun updateInfo(connection: Connection, updates: Collection<JukeboxInfo>) {
        val select =
            connection.prepareStatement("SELECT id FROM info_cache WHERE id=?;")
        val update = connection.prepareStatement("UPDATE info_cache SET song_name=?,song_title=?,song_artist=?,song_url=?,song_duration=? WHERE id=?;")
        val insert =
            connection.prepareStatement("INSERT INTO info_cache(id, song_name, song_title, song_artist, song_url, song_duration) VALUES (?, ?, ?, ?, ?, ?);")

        updates.chunked(100) { chunk ->
            insert.clearBatch()
            update.clearBatch()
            update.clearParameters()

            chunk.forEach { info ->
                select.setString(1, info.id)
                select.execute()

                select.resultSet.use { rs ->
                    if (rs.next()) {
                        update.setString(1, info.name)
                        update.setString(2, info.title)
                        update.setString(3, info.artist)
                        update.setString(4, info.url)
                        update.setInt(5, info.duration)
                        update.setString(6, info.id)
                        update.addBatch()
                    } else {
                        insert.setString(1, info.id)
                        insert.setString(2, info.name)
                        insert.setString(3, info.title)
                        insert.setString(4, info.artist)
                        insert.setString(5, info.url)
                        insert.setInt(6, info.duration)
                        insert.addBatch()
                    }
                }
            }

            insert.executeBatch()
            update.executeBatch()
        }
    }

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
        GlobalScope.launch(dispatcher) { popularUpdates[service]?.send(id) }

//        use { connection ->
//
//
//            Unit
//        }
    }

    init {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:h2:./$databaseName;mode=MySQL"

        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

        ds = HikariDataSource(config)

        initialise()
    }
}