package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object H2Database: HikariDatabase() {
    override val ds: HikariDataSource

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