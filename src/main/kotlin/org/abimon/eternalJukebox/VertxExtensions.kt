package org.abimon.eternalJukebox

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.ConstantValues
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.readChunked
import kotlin.reflect.KClass

fun HttpServerResponse.end(json: JsonArray) = putHeader("Content-Type", "application/json").end(json.toString())
fun HttpServerResponse.end(json: JsonObject) = putHeader("Content-Type", "application/json").end(json.toString())

fun HttpServerResponse.end(init: JsonObject.() -> Unit) {
    val json = JsonObject()
    json.init()

    putHeader("Content-Type", "application/json").end(json.toString())
}

fun RoutingContext.endWithStatusCode(statusCode: Int, init: JsonObject.() -> Unit) {
    val json = JsonObject()
    json.init()

    this.response().setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .putHeader("X-Client-UID", clientInfo.userUID)
            .end(json.toString())
}

fun HttpServerResponse.end(data: DataSource, contentType: String = "application/octet-stream") {
    putHeader("Content-Type", contentType)
    putHeader("Content-Length", "${data.size}")
    data.use { stream -> stream.readChunked { chunk -> write(Buffer.buffer(chunk)) } }
    end()
}

fun HttpServerResponse.redirect(url: String): Unit = putHeader("Location", url).setStatusCode(307).end()
fun HttpServerResponse.redirect(builderAction: StringBuilder.() -> Unit): Unit = putHeader("Location", StringBuilder().apply(builderAction).toString()).setStatusCode(307).end()

operator fun RoutingContext.set(key: String, value: Any) = put(key, value)
operator fun <T : Any> RoutingContext.get(key: String, @Suppress("UNUSED_PARAMETER") klass: KClass<T>): T? = get<T>(key)
operator fun <T : Any> RoutingContext.get(key: String, default: T): T = get<T>(key) ?: default
operator fun <T : Any> RoutingContext.get(key: String, default: T, @Suppress("UNUSED_PARAMETER") klass: KClass<T>): T = get<T>(key)
        ?: default

val RoutingContext.clientInfo: ClientInfo
    get() {
        if (ConstantValues.CLIENT_INFO in data() && data()[ConstantValues.CLIENT_INFO] is ClientInfo)
            return data()[ConstantValues.CLIENT_INFO] as ClientInfo

        val info = ClientInfo(this)

        data()[ConstantValues.CLIENT_INFO] = info

        return info
    }

fun <T> executeBlocking(operation: () -> T, onComplete: (AsyncResult<T>) -> Unit) {
    EternalJukebox.vertx.executeBlocking(Handler<Future<T>> { future ->
        future.complete(operation())
    }, Handler { result -> onComplete(result) })
}

operator fun JsonObject.set(key: String, value: Any) = put(key, value)