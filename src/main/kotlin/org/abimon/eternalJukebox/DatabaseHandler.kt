package org.abimon.eternalJukebox

import io.vertx.ext.web.RoutingContext
import org.abimon.visi.collections.Pool
import org.abimon.visi.collections.PoolableObject
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.*
import java.util.concurrent.TimeUnit

val mysqlConnectionPool = Pool<Connection>(128)
fun getConnection(): PoolableObject<Connection> = mysqlConnectionPool.getOrAddOrWait(60, TimeUnit.SECONDS, ::makeConnection) as PoolableObject<Connection>

fun createAccountsTable() = getConnection().use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS accounts (id VARCHAR(63) PRIMARY KEY, discord_id VARCHAR(24), discord_token VARCHAR(255), refresh_token VARCHAR(255));") }
fun createPopularJukeboxTable() = getConnection().use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS popular_jukebox (id VARCHAR(63) PRIMARY KEY, hits INT);") }
fun createPopularCanonizerTable() = getConnection().use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS popular_canonizer (id VARCHAR(63) PRIMARY KEY, hits INT);") }
fun createRequestsTable() = getConnection().use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS requests (id INT PRIMARY KEY AUTO_INCREMENT, time LONG, ip VARCHAR(255), request VARCHAR(255));") }
fun ResultSet.toIterable(): Iterable<ResultSet> {
    val results = this

    return object : Iterable<ResultSet> {
        val iterator = object : Iterator<ResultSet> {
            override fun hasNext(): Boolean = results.next()

            override fun next(): ResultSet = results
        }

        override fun iterator(): Iterator<ResultSet> {
            results.beforeFirst()
            return iterator
        }
    }
}

fun Statement.executeAndClose(sql: String) {
    execute(sql)
    close()
}

fun Statement.executeQueryAndClose(sql: String): ResultSet {
    closeOnCompletion()
    return executeQuery(sql)
}

val popularised = HashMap<String, Long>()
fun populariseJukebox(context: RoutingContext) {
    val request = context.request()
    val id = request.getParam("id").replace("[^A-Za-z0-9]".toRegex(), "")

    val key = "$id:jukebox:${request.remoteAddress().host()}"
    if (popularised.containsKey(key)) {
        val expires = popularised[key]!!
        if (expires > System.currentTimeMillis())
            return
    }

    popularised[key] = System.currentTimeMillis() + 1000 * 60 * 60 //1 hr cooldown

    getConnection().use { connection ->
        val preparedSelect = connection.prepareStatement("SELECT id FROM popular_jukebox WHERE id=?")
        preparedSelect.setString(1, id)
        preparedSelect.execute()
        val results = preparedSelect.resultSet
        if (!results.isBeforeFirst) {
            val prepared = connection.prepareStatement("INSERT INTO popular_jukebox VALUES(?, 1)")
            prepared.setString(1, id)
            prepared.execute()
            prepared.close()
        } else {
            val prepared = connection.prepareStatement("UPDATE popular_jukebox SET hits = hits + 1 WHERE id=?")
            prepared.setString(1, id)
            prepared.execute()
            prepared.close()
        }
        preparedSelect.close()
    }
}
fun populariseCanonizer(context: RoutingContext) {
    val request = context.request()
    val id = request.getParam("id").replace("[^A-Za-z0-9]".toRegex(), "")

    val key = "$id:canonizer:${request.remoteAddress().host()}"
    if (popularised.containsKey(key)) {
        val expires = popularised[key]!!
        if (expires > System.currentTimeMillis())
            return
    }

    popularised[key] = System.currentTimeMillis() + 1000 * 60 * 60 //1 hr cooldown

    getConnection().use { connection ->
        val preparedSelect = connection.prepareStatement("SELECT id FROM popular_canonizer WHERE id=?")
        preparedSelect.setString(1, id)
        preparedSelect.execute()
        val results = preparedSelect.resultSet
        if (!results.isBeforeFirst) {
            val prepared = connection.prepareStatement("INSERT INTO popular_canonizer VALUES(?, 1)")
            prepared.setString(1, id)
            prepared.execute()
            prepared.close()
        } else {
            val prepared = connection.prepareStatement("UPDATE popular_canonizer SET hits = hits + 1 WHERE id=?")
            prepared.setString(1, id)
            prepared.execute()
            prepared.close()
        }
        preparedSelect.close()
    }
}

fun getPopularJukeboxSongs(max: Int = 30): List<String> {
    val list = ArrayList<String>()
    getConnection().use { connection ->
        val preparedSelect = connection.prepareStatement("SELECT * FROM popular_jukebox")
        preparedSelect.execute()
        val results = preparedSelect.resultSet
        if (results.isBeforeFirst) {
            val resultList = results.toIterable().map { result -> Pair(result.getString("id"), result.getInt("hits")) }.sortedWith(Comparator<Pair<String, Int>> { (_, oneHits), (_, twoHits) -> oneHits.compareTo(twoHits) }).reversed()
            list.addAll(resultList.subList(0, resultList.size.coerceAtMost(max)).map { (id) -> id })
        }
    }

    return list
}

fun getPopularCanonizerSongs(max: Int = 30): List<String> {
    val list = ArrayList<String>()
    getConnection().use { connection ->
        val preparedSelect = connection.prepareStatement("SELECT * FROM popular_canonizer")
        preparedSelect.execute()
        val results = preparedSelect.resultSet
        if (results.isBeforeFirst) {
            val resultList = results.toIterable().map { result -> Pair(result.getString("id"), result.getInt("hits")) }.sortedWith(Comparator<Pair<String, Int>> { (_, oneHits), (_, twoHits) -> oneHits.compareTo(twoHits) }).reversed()
            list.addAll(resultList.subList(0, resultList.size.coerceAtMost(max)).map { (id) -> id })
        }
    }

    return list
}

fun logRequest(ctx: RoutingContext) {
    getConnection().use { connection ->
        val prepared = connection.prepareStatement("INSERT INTO requests(time, ip, request) VALUES(?, ?, ?)")
        prepared.setLong(1, System.currentTimeMillis())
        prepared.setString(2, ctx.request().remoteAddress().toString())
        prepared.setString(3, ctx.request().path())
        prepared.execute()
        prepared.close()
    }
    ctx.next()
}