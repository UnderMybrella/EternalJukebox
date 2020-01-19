package dev.eternalbox.eternaljukebox

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.github.kittinunf.fuel.core.FuelError
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.reflect.jvm.jvmName

val VERTX_MODULE: Module = SimpleModule().apply {
    // custom types
    addSerializer(JsonObject::class.java, object : JsonSerializer<JsonObject>() {
        override fun handledType(): Class<JsonObject> = JsonObject::class.java
        override fun serialize(value: JsonObject, jgen: JsonGenerator, provider: SerializerProvider?) {
            jgen.writeObject(value.map)
        }
    })
    addSerializer(JsonArray::class.java, object : JsonSerializer<JsonArray>() {
        override fun handledType(): Class<JsonArray> = JsonArray::class.java
        override fun serialize(value: JsonArray, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeObject(value.list)
        }
    })

    addSerializer(Exception::class.java, object: JsonSerializer<Exception>() {
        override fun handledType(): Class<Exception> = java.lang.Exception::class.java
        override fun serialize(value: Exception, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeStartObject()
            gen.writeObjectField("type", value::class.qualifiedName ?: value::class.jvmName)
            gen.writeObjectField("message", value.message)
            gen.writeObjectField("localised_message", value.localizedMessage)
            val baos = ByteArrayOutputStream()
            PrintStream(baos).use(value::printStackTrace)
            gen.writeObjectField("stacktrace", String(baos.toByteArray()))
            gen.writeEndObject()
        }
    })
}

val JSON_MAPPER: ObjectMapper = ObjectMapper()
    .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule(), VERTX_MODULE)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

val JSON_PRETTY_MAPPER: ObjectMapper = ObjectMapper()
    .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule(), VERTX_MODULE)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

val YAML_MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
    .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule(), VERTX_MODULE)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

fun jsonStringOfObject(vararg pairs: Pair<String, Any>): String = JSON_MAPPER.writeValueAsString(pairs.toMap())

suspend inline fun <reified T> FuelResult<ByteArray, FuelError>.asResult(): JukeboxResult<T> =
    getResponseFromResult(this)

suspend inline fun <reified T> getResponseFromResult(result: FuelResult<ByteArray, FuelError>): JukeboxResult<T> =
    getResponseFromResult(result.component1(), result.component2())

suspend inline fun <reified T> getResponseFromResult(
    data: ByteArray?,
    error: FuelError?
): JukeboxResult<T> =
    when {
        data != null -> JukeboxResult.Success(withContext(Dispatchers.IO) { JSON_MAPPER.readValue<T>(data) })
        error != null -> {
            JukeboxResult.KnownFailure(
                error.response.statusCode,
                error.response.responseMessage,
                withContext(Dispatchers.IO) { JSON_MAPPER.readTree(error.response.data) }
            )
        }
        else -> JukeboxResult.UnknownFailure()
    }

suspend inline fun FuelResult<ByteArray, FuelError>.asTree(): JukeboxResult<JsonNode> =
    getTreeResponseFromResult(this)

suspend inline fun getTreeResponseFromResult(result: FuelResult<ByteArray, FuelError>): JukeboxResult<JsonNode> =
    getTreeResponseFromResult(result.component1(), result.component2())

suspend inline fun getTreeResponseFromResult(
    data: ByteArray?,
    error: FuelError?
): JukeboxResult<JsonNode> =
    when {
        data != null -> JukeboxResult.Success(withContext(Dispatchers.IO) { JSON_MAPPER.readTree(data) })
        error != null -> {
            JukeboxResult.KnownFailure(
                error.response.statusCode,
                error.response.responseMessage,
                withContext(Dispatchers.IO) { JSON_MAPPER.readTree(error.response.data) }
            )
        }
        else -> JukeboxResult.UnknownFailure()
    }