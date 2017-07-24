package org.abimon.eternalJukebox

import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.readChunked

fun HttpServerResponse.end(json: JsonArray) = putHeader("Content-Type", "application/json").end(json.toString())
fun HttpServerResponse.end(json: JsonObject) = putHeader("Content-Type", "application/json").end(json.toString())

fun HttpServerResponse.end(data: DataSource, contentType: String = "application/octet-stream") {
    putHeader("Content-Type", contentType)
    data.use { stream -> stream.readChunked { chunk -> write(Buffer.buffer(chunk)) } }
    end()
}