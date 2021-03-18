package org.abimon.eternalJukebox.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

object JDBCDatabase: HikariDatabase() {
    override val ds: HikariDataSource

    init {
        Class.forName("com.mysql.jdbc.Driver")
            .getDeclaredConstructor()
            .newInstance()

        val config = HikariConfig("hikari.properties")
        config.jdbcUrl = databaseOptions["jdbcUrl"]?.toString() ?: throw IllegalStateException("jdbcUrl was not provided!")

        config.username = databaseOptions["username"]?.toString()
        config.password = databaseOptions["password"]?.toString()
        config.initializationFailTimeout = 0

        val cloudSqlInstance = databaseOptions["cloudSqlInstance"]?.toString()

        if(cloudSqlInstance != null) {
            config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.mysql.SocketFactory")
            config.addDataSourceProperty("cloudSqlInstance", cloudSqlInstance)
        }

//        config.addDataSourceProperty("useServerPrepStmts", databaseOptions["userServerPrepStmts"]?.toString() ?: "true")
//        config.addDataSourceProperty("cachePrepStmts", databaseOptions["cachePrepStmts"]?.toString() ?: "true")
//        config.addDataSourceProperty("prepStmtCacheSize", databaseOptions["prepStmtCacheSize"]?.toString() ?: "250")
//        config.addDataSourceProperty("prepStmtCacheSqlLimit", databaseOptions["prepStmtCacheSqlLimit"]?.toString() ?: "2048")
//        config.addDataSourceProperty("useLocalSessionState", "true")
//        config.addDataSourceProperty("rewriteBatchedStatements", "true")
//        config.addDataSourceProperty("cacheResultSetMetadata", "true")
//        config.addDataSourceProperty("cacheServerConfiguration", "true")
//        config.addDataSourceProperty("elideSetAutoCommits", "true")
//        config.addDataSourceProperty("maintainTimeStats", "false")

        ds = HikariDataSource(config)

        initialise()
    }
}