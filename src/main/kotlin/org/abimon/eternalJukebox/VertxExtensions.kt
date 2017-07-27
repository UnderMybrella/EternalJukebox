package org.abimon.eternalJukebox

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.readChunked
import kotlin.reflect.KClass

fun HttpServerResponse.end(json: JsonArray) = putHeader("Content-Type", "application/json").end(json.toString())
fun HttpServerResponse.end(json: JsonObject) = putHeader("Content-Type", "application/json").end(json.toString())

fun HttpServerResponse.end(data: DataSource, contentType: String = "application/octet-stream") {
    putHeader("Content-Type", contentType)
    putHeader("Content-Length", "${data.size}")
    data.use { stream -> stream.readChunked { chunk -> write(Buffer.buffer(chunk)) } }
    end()
}

fun HttpServerResponse.redirect(url: String): Unit = putHeader("Location", url).setStatusCode(302).end()

operator fun RoutingContext.set(key: String, value: Any) = put(key, value)
operator fun <T: Any> RoutingContext.get(key: String, klass: KClass<T>): T? = get<T>(key)
operator fun <T: Any> RoutingContext.get(key: String, default: T, klass: KClass<T>): T = get<T>(key) ?: default