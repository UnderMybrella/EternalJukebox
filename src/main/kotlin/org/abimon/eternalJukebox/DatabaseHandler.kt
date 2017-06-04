package org.abimon.eternalJukebox

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.objects.EternalUser
import org.abimon.eternalJukebox.objects.GoogleToken
import org.abimon.notifly.notification
import org.abimon.visi.collections.Pool
import org.abimon.visi.collections.PoolableObject
import org.abimon.visi.lang.Snowstorm
import org.abimon.visi.lang.asOptional
import org.abimon.visi.security.sha512Hash
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.util.*
import java.util.concurrent.TimeUnit

val mysqlConnectionPool = Pool<Connection>(128)
val secureRandom = SecureRandom()
val popularised = HashMap<String, Long>()
val snowstorm = Snowstorm.getInstance(config.epoch)
private val toBase64URL = charArrayOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_')

fun getConnection(): Optional<PoolableObject<Connection>> = if (mysqlEnabled()) Optional.of(mysqlConnectionPool.getOrAddOrWait(60, TimeUnit.SECONDS, ::makeConnection) as PoolableObject<Connection>) else Optional.empty()

fun createAccountsTable() = getConnection().ifPresent { it.use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS accounts (id VARCHAR(63) PRIMARY KEY, access_token VARCHAR(255), google_id VARCHAR(63), google_access_token VARCHAR(255), google_refresh_token VARCHAR(255), expires_at LONG);") } }
fun createPopularJukeboxTable() = getConnection().ifPresent { it.use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS popular_jukebox (id VARCHAR(63) PRIMARY KEY, hits INT);") } }
fun createPopularCanonizerTable() = getConnection().ifPresent { it.use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS popular_canonizer (id VARCHAR(63) PRIMARY KEY, hits INT);") } }
fun createShortURLTable() = getConnection().ifPresent { it.use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS short_urls (id VARCHAR(16) PRIMARY KEY, params VARCHAR(4096));") } }
fun createRequestsTable() = getConnection().ifPresent { it.use { connection -> connection.createStatement().executeAndClose("CREATE TABLE IF NOT EXISTS requests (id INT PRIMARY KEY AUTO_INCREMENT, time LONG, ip VARCHAR(255), request VARCHAR(255));") } }

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

fun populariseJukebox(context: RoutingContext) {
    val request = context.request()
    val id = (request.getParam("id") ?: "").replace("[^A-Za-z0-9]".toRegex(), "")

    val key = "$id:jukebox:${request.remoteAddress().host()}"
    if (popularised.containsKey(key)) {
        val expires = popularised[key]!!
        if (expires > System.currentTimeMillis())
            return
    }

    popularised[key] = System.currentTimeMillis() + 1000 * 60 * 60 //1 hr cooldown

    getConnection().ifPresent {
        it.use { connection ->
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
}

fun populariseCanonizer(context: RoutingContext) {
    val request = context.request()
    val id = (request.getParam("id") ?: "").replace("[^A-Za-z0-9]".toRegex(), "")

    val key = "$id:canonizer:${request.remoteAddress().host()}"
    if (popularised.containsKey(key)) {
        val expires = popularised[key]!!
        if (expires > System.currentTimeMillis())
            return
    }

    popularised[key] = System.currentTimeMillis() + 1000 * 60 * 60 //1 hr cooldown

    getConnection().ifPresent {
        it.use { connection ->
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
}

fun getPopularJukeboxSongs(max: Int = 30): List<String> {
    val list = ArrayList<String>()
    getConnection().ifPresent {
        it.use { connection ->
            val preparedSelect = connection.prepareStatement("SELECT * FROM popular_jukebox")
            preparedSelect.execute()
            val results = preparedSelect.resultSet
            if (results.isBeforeFirst) {
                val resultList = results.toIterable().map { result -> Pair(result.getString("id"), result.getInt("hits")) }.sortedWith(Comparator<Pair<String, Int>> { (_, oneHits), (_, twoHits) -> oneHits.compareTo(twoHits) }).reversed()
                list.addAll(resultList.subList(0, resultList.size.coerceAtMost(max)).map { (id) -> id })
            }
        }
    }

    return list
}

fun getUserByToken(token: DecodedJWT): EternalUser? {
    var user: EternalUser? = null

    getConnection().ifPresent {
        it.use { connection ->
            val preparedSelect = connection.prepareStatement("SELECT * FROM accounts WHERE id=? AND access_token=?")
            preparedSelect.setString(1, token.subject)
            preparedSelect.setString(2, token.getClaim("access_token").asString())
            preparedSelect.execute()
            val results = preparedSelect.resultSet
            if (results.next()) {
                user = EternalUser(results.getString("id"), results.getString("access_token"), results.getString("google_id"), results.getString("google_access_token"), results.getString("google_refresh_token"), results.getLong("expires_at"))
            }
        }
    }

    return user
}

/** Returns a cookie */
fun createOrUpdateUser(googleToken: GoogleToken): String {
    var cookie = ""
    val googleUser = getGoogleUser(googleToken.access_token)

    getConnection().ifPresent {
        it.use { connection ->
            val preparedSelect = connection.prepareStatement("SELECT * FROM accounts WHERE google_id=?")
            preparedSelect.setString(1, googleUser.id)
            preparedSelect.execute()
            val results = preparedSelect.resultSet
            if (results.next()) {
                val user = EternalUser(results.getString("id"), results.getString("access_token"), results.getString("google_id"), results.getString("google_access_token"), results.getString("google_refresh_token"), results.getLong("expires_at"))
                val updateUser = connection.prepareStatement("UPDATE accounts SET google_access_token=?, google_refresh_token=?, expires_at=?,access_token=? WHERE id=? OR google_id=?")
                updateUser.setString(1, googleToken.access_token)
                updateUser.setString(2, googleToken.refresh_token.orElse(user.googleRefreshToken))
                updateUser.setLong(3, System.currentTimeMillis() + googleToken.expires_in)
                updateUser.setString(4, user.accessToken)
                updateUser.setString(5, user.id)
                updateUser.setString(6, user.googleID)
                updateUser.execute()
                updateUser.close()

                cookie = JWT.create()
                        .withIssuer(config.ip)
                        .withSubject(user.id)
                        .withClaim("access_token", user.accessToken)
                        .sign(API.hmacSign)
            } else {
                val newUser = connection.prepareStatement("INSERT INTO accounts VALUES (?, ?, ?, ?, ?, ?)")
                val id = snowstorm.get()
                val token = genRandomToken()
                newUser.setString(1, id)
                newUser.setString(2, token)
                newUser.setString(3, googleUser.id)
                newUser.setString(4, googleToken.access_token)
                newUser.setString(5, googleToken.refresh_token.orElse(""))
                newUser.setLong(6, System.currentTimeMillis() + googleToken.expires_in)
                newUser.execute()
                newUser.close()

                cookie = JWT.create()
                        .withIssuer(config.ip)
                        .withSubject(id)
                        .withClaim("access_token", token)
                        .sign(API.hmacSign)
            }
            preparedSelect.close()
        }
    }

    return cookie
}

fun genRandomToken(): String {
    val bytes = ByteArray(64)
    secureRandom.nextBytes(bytes)
    return bytes.sha512Hash()
}

fun getPopularCanonizerSongs(max: Int = 30): List<String> {
    val list = ArrayList<String>()
    getConnection().ifPresent {
        it.use { connection ->
            val preparedSelect = connection.prepareStatement("SELECT * FROM popular_canonizer")
            preparedSelect.execute()
            val results = preparedSelect.resultSet
            if (results.isBeforeFirst) {
                val resultList = results.toIterable().map { result -> Pair(result.getString("id"), result.getInt("hits")) }.sortedWith(Comparator<Pair<String, Int>> { (_, oneHits), (_, twoHits) -> oneHits.compareTo(twoHits) }).reversed()
                list.addAll(resultList.subList(0, resultList.size.coerceAtMost(max)).map { (id) -> id })
            }
        }
    }

    return list
}

fun getOrShrinkParams(parameters: String): String {
    var paramString = parameters
    while (paramString.length > 4096) {
        if (paramString.indexOf('&') == -1)
            paramString = "id=4uLU6hMCjMI75M1A2tKUQC"
        else
            paramString = paramString.substringBeforeLast("&")
    }

    if (paramString.indexOf('=') == -1)
        paramString = "id=4uLU6hMCjMI75M1A2tKUQC"

    println(paramString)

    var id = ""

    getConnection().ifPresent {
        it.use { connection ->
            connection.prepareStatement("SELECT * FROM short_urls WHERE params=?").use { preparedSelect ->
                preparedSelect.setString(1, paramString)
                preparedSelect.execute()
                val results = preparedSelect.resultSet
                if (results.next()) {
                    id = results.getString("id")
                } else {
                    val newURL = connection.prepareStatement("INSERT INTO short_urls VALUES (?, ?)")
                    id = obtainNewShortID(connection)
                    newURL.setString(1, id)
                    newURL.setString(2, paramString)
                    newURL.execute()
                    newURL.close()
                }
                results.close()
            }
        }
    }

    return id
}

fun expand(shortID: String): String? {
    var params: String? = null

    getConnection().ifPresent {
        it.use { connection ->
            val preparedSelect = connection.prepareStatement("SELECT * FROM short_urls WHERE id=?")
            preparedSelect.setString(1, shortID)
            preparedSelect.execute()
            val results = preparedSelect.resultSet
            if (results.next())
                params = results.getString("params")
            results.close()
            preparedSelect.close()
        }
    }

    return params
}

fun obtainNewShortID(connection: Connection): String {
    val preparedSelect = connection.prepareStatement("SELECT * FROM short_urls WHERE id=?")
    val range = (0 until config.shortIDLength)
    (0 until 4096).forEach {
        val id = range.map { toBase64URL[secureRandom.nextInt(64)] }.joinToString("")
        preparedSelect.setString(1, id)
        preparedSelect.execute()
        preparedSelect.resultSet.use {
            if (!it.isBeforeFirst)
                return@obtainNewShortID id
        }

        println("Generated $id, no success")
    }

    sendFirebaseMessage(notification {
        title("Run out of ${config.shortIDLength} char IDs")
        body("We've run out of new short IDs to send. This is bad.")
    }.asOptional())

    throw IllegalStateException("Run out of IDs")
}

fun logRequest(ctx: RoutingContext) {
    getConnection().ifPresent {
        it.use { connection ->
            val prepared = connection.prepareStatement("INSERT INTO requests(time, ip, request) VALUES(?, ?, ?)")
            prepared.setLong(1, System.currentTimeMillis())
            prepared.setString(2, ctx.request().remoteAddress().toString())
            prepared.setString(3, ctx.request().path())
            prepared.execute()
            prepared.close()
        }
    }
    ctx.next()
}