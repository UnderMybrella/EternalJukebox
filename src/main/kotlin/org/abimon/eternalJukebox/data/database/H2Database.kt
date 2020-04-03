package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.abimon.eternalJukebox.objects.ClientInfo

object H2Database: HikariDatabase() {
    override val ds: HikariDataSource

    override fun makeSongPopular(service: String, id: String, clientInfo: ClientInfo?) {
        use { connection ->
            val select =
                connection.prepareStatement("SELECT id, hits FROM popular WHERE service=? AND song_id=? ORDER BY hits DESC;")
            select.setString(1, service)
            select.setString(2, id)
            select.execute()

            val results = select.resultSet
            if (results.next()) {
                val update = connection.prepareStatement("UPDATE popular SET hits=hits + 1 WHERE id=?;")
                update.setLong(1, results.getLong("id"))

                update.execute()
            } else {
                val insert =
                    connection.prepareStatement("INSERT INTO popular (song_id, service, hits) VALUES (?, ?, ?);")
                insert.setString(1, id)
                insert.setString(2, service)
                insert.setLong(3, 1)

                insert.execute()
            }

            Unit
        }
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