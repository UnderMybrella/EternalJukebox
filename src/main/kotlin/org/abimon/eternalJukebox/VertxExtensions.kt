package org.abimon.eternalJukebox

import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray

fun HttpServerResponse.end(json: JsonArray) = putHeader("Content-Type", "application/json").end(json.toString())