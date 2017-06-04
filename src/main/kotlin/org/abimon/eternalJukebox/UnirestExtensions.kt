package org.abimon.eternalJukebox

import com.fasterxml.jackson.core.JsonProcessingException
import com.mashape.unirest.http.HttpResponse
import com.mashape.unirest.http.JsonNode
import org.abimon.eternalJukebox.objects.ErroredResponse
import kotlin.reflect.KClass

typealias HttpStatus = Pair<Int, String>

val HttpResponse<*>.statusPair: HttpStatus
    get() = status to statusText

public infix fun <R> R?.withHttpError(e: HttpStatus?): ErroredResponse<R?, HttpStatus?> = ErroredResponse(this, e)

public infix fun <T : Any> JsonNode.mapTo(klass: KClass<T>): T? {
    try {
        return objMapper.readValue(this.toString(), klass.java)
    } catch(json: JsonProcessingException) {
        return null
    }
}