package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.databind.ObjectMapper
import dev.eternalbox.eternaljukebox.data.DataResponse
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import dev.eternalbox.eternaljukebox.data.JukeboxRoutingContext
import dev.eternalbox.eternaljukebox.data.WebApiResponseCodes
import dev.eternalbox.eternaljukebox.routes.EternalboxRoute
import dev.eternalbox.ytmusicapi.MutableUnknownJsonObj
import dev.eternalbox.ytmusicapi.UnknownJsonObj
import io.netty.handler.codec.http.HttpHeaderNames
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.http.endAwait
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.*
import kotlin.collections.HashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

class SuspensionHandler<T>(val func: suspend (T) -> Unit, val scope: CoroutineScope, val context: CoroutineContext) :
    Handler<T> {
    override fun handle(event: T) {
        scope.launch(context) { func(event) }
    }
}

@ExperimentalContracts
class SuspensionRouteHandler(
    val func: suspend JukeboxRoutingContext.() -> Unit,
    val jukebox: EternalJukebox,
    val scope: CoroutineScope,
    val context: CoroutineContext
) : Handler<RoutingContext> {
    override fun handle(event: RoutingContext) {
        scope.launch(context) { JukeboxRoutingContext(jukebox, event).func() }
    }
}

fun json(build: JsonObjectBuilder.() -> Unit): UnknownJsonObj {
    return JsonObjectBuilder().json(build)
}

class JsonObjectBuilder {
    private val deque: Deque<MutableUnknownJsonObj> = ArrayDeque()

    fun json(build: JsonObjectBuilder.() -> Unit): UnknownJsonObj {
        deque.push(HashMap())
        this.build()
        return deque.pop()
    }

    operator fun <T : Any> String.rangeTo(value: T) {
        deque.peek()[this] = value
    }
}

fun JsonObject.encodeWith(mapper: ObjectMapper): String = mapper.writeValueAsString(this)

fun Route.suspendHandler(
    coroutineScope: CoroutineScope,
    context: CoroutineContext,
    func: suspend (RoutingContext) -> Unit
) = handler(SuspensionHandler(func, coroutineScope, context))

fun Route.suspendFailureHandler(
    coroutineScope: CoroutineScope,
    context: CoroutineContext,
    func: suspend (RoutingContext) -> Unit
) = failureHandler(SuspensionHandler(func, coroutineScope, context))

fun Router.suspendErrorHandler(
    errorCode: Int,
    coroutineScope: CoroutineScope,
    context: CoroutineContext,
    func: suspend (RoutingContext) -> Unit
) = errorHandler(errorCode, SuspensionHandler(func, coroutineScope, context))

@ExperimentalContracts
fun RoutingContext.errorMessage(languageData: LanguageData, key: String): String? =
    languageData.errorMessage(acceptableLanguages(), key)

suspend fun HttpServerResponse.endJsonAwait(build: JsonObjectBuilder.() -> Unit) {
    val json = JsonObjectBuilder().json(build)

    putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
    endAwait(withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsString(json) })
}

@ExperimentalCoroutinesApi
suspend fun HttpServerResponse.endAwait(channel: ReceiveChannel<Buffer>) = channel.copyTo(this)

suspend fun HttpServerResponse.endAwait(result: JukeboxResult.Failure<*>) {
    when (result) {
        is JukeboxResult.KnownFailure<*, *> ->
            setStatusCode(WebApiResponseCodes getHttpStatusCode result.errorCode)
                .endJsonAwait {
                    "error" .. result.errorCode
                    "message" .. result.errorMessage
                    if (result.additionalInfo != null)
                        "additional" .. result.additionalInfo
                }
        is JukeboxResult.UnknownFailure ->
            setStatusCode(500)
                .endJsonAwait {
                    "error" .. 0
                    "message" .. "An unknown error occurred"
                }
    }
}

@ExperimentalCoroutinesApi
suspend fun HttpServerResponse.endAwait(data: DataResponse, coroutineScope: CoroutineScope) {
    when (data) {
        is DataResponse.ExternalUrl ->
            putHeader("Location", data.url)
                .setStatusCode(data.redirectCode)
                .endAwait()

        is DataResponse.DataSource ->
            putHeader(HttpHeaderNames.CONTENT_LENGTH, data.size.toString())
                .putHeader(HttpHeaderNames.CONTENT_TYPE, data.contentType)
                .endAwait(data.source().asVertxChannel(coroutineScope, Dispatchers.IO))

        is DataResponse.Data ->
            putHeader(HttpHeaderNames.CONTENT_TYPE, data.contentType)
                .endAwait(Buffer.buffer(data.data))
    }
}

@ExperimentalContracts
public inline fun <R> EternalboxRoute.routeWith(receiver: RoutingContext, block: JukeboxRoutingContext.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return JukeboxRoutingContext(jukebox, receiver).block()
}

@ExperimentalContracts
public inline fun <R> EternalJukebox.routeWith(receiver: RoutingContext, block: JukeboxRoutingContext.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return JukeboxRoutingContext(this, receiver).block()
}