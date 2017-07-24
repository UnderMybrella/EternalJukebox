package org.abimon.eternalJukebox

import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

fun HttpServerResponse.end(json: JsonArray) = putHeader("Content-Type", "application/json").end(json.toString())
fun HttpServerResponse.end(json: JsonObject) = putHeader("Content-Type", "application/json").end(json.toString())