package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object JDBCDatabase: HikariDatabase() {
    override val ds: HikariDataSource

    init {
        val config = HikariConfig()
        config.jdbcUrl = databaseOptions["jdbcUrl"]?.toString() ?: throw IllegalStateException("jdbcUrl was not provided!")

        config.username = databaseOptions["username"]?.toString()
        config.password = databaseOptions["password"]?.toString()

        val cloudSqlInstance = databaseOptions["cloudSqlInstance"]?.toString()

        if(cloudSqlInstance != null) {
            config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory")
            config.addDataSourceProperty("cloudSqlInstance", cloudSqlInstance)
        }

        config.addDataSourceProperty("cachePrepStmts", databaseOptions["cachePrepStmts"]?.toString() ?: "true")
        config.addDataSourceProperty("prepStmtCacheSize", databaseOptions["prepStmtCacheSize"]?.toString() ?: "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", databaseOptions["prepStmtCacheSqlLimit"]?.toString() ?: "2048")

        ds = HikariDataSource(config)

        initialise()
    }
}