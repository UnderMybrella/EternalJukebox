package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.abimon.eternalJukebox.objects.ClientInfo
import java.sql.Connection

object H2Database : HikariDatabase() {
    override val ds: HikariDataSource

    override fun updatePopular(connection: Connection, updates: Map<String, Int>) {
        val select =
            connection.prepareStatement("SELECT id FROM popular WHERE service=? AND song_id=? ORDER BY hits DESC;")
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
                        update.execute()
                    } else {
                        insert.setString(1, key.substringAfter(':'))
                        insert.setString(2, key.substringBefore(':'))
                        insert.setInt(3, amount)
                        insert.execute()
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