package org.abimon.eternalJukebox

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.abimon.visi.collections.copyFrom
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

fun log(msg: String, error: Boolean = false) {
    val there = Thread.currentThread().stackTrace.copyFrom(1).firstOrNull { it.className != "org.abimon.eternalJukebox.GeneralUtilsKt" && !it.className.contains('$') }
            ?: run {
                if (error)
                    System.err.println("[Unknown] $msg")
                else
                    println("[Unknown] $msg")
                return@log
            }

    val className = run {
        try {
            return@run Class.forName(there.className).simpleName
        } catch (notFound: ClassNotFoundException) {
            return@run there.className
        }
    }

    (EternalJukebox.logStreams[className] ?: (if (error) System.err else System.out)).println("[$className -> ${there.methodName}] $msg")

//    if (error)
//        System.err.println("[$className -> ${there.methodName}] $msg")
//    else
//        println("[$className -> ${there.methodName}] $msg")
}

fun <T> logNull(msg: String, error: Boolean = false): T? {
    log(msg, error)
    return null
}

/**
 * @param task return true if we need to back off
 */
fun exponentiallyBackoff(maximumBackoff: Long, maximumTries: Long, task: (Long) -> Boolean): Boolean {
    if (!task(0))
        return true

    val rng = Random()

    for (i in 0 until maximumTries) {
        if (!task(i))
            return true

        Thread.sleep(Math.min((Math.pow(2.0, i.toDouble()) + rng.nextInt(1000)).toLong(), maximumBackoff))
    }

    return false
}

fun Any.toJsonObject(): JsonObject = JsonObject(EternalJukebox.jsonMapper.writeValueAsString(this))

fun jsonObject(init: JsonObject.() -> Unit): JsonObject {
    val json = JsonObject()
    json.init()
    return json
}

fun jsonObjectOf(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(pairs.toMap())

/**
 * Perform an action with this file if it exists, and then delete it.
 * Returns null if the file does not exist
 */
fun <T> File.useThenDelete(action: (File) -> T): T? {
    try {
        if (exists())
            return action(this)
        else
            return null
    } finally {
        guaranteeDelete()
    }
}

fun File.guaranteeDelete() {
    delete()
    if (exists()) {
        log("$this was not deleted successfully; deleting on exit and starting coroutine", error = true)
        deleteOnExit()

        launch {
            val rng = Random()
            var i = 0
            while (isActive && exists()) {
                delete()
                if (!exists()) {
                    log("Finally deleted $this after $i attempts")
                }

                delay(Math.min((Math.pow(2.0, (i++).toDouble()) + rng.nextInt(1000)).toLong(), 64000), TimeUnit.MILLISECONDS)
            }
        }
    }
}

fun Reader.useAndFilterLine(predicate: (String) -> Boolean): String? = this.use { reader -> reader.readLines().firstOrNull(predicate) }

fun Reader.useLineByLine(op: (String) -> Unit) = this.use { reader -> reader.readLines().forEach(op) }

val KClass<*>.simpleClassName: String
    get() = simpleName ?: jvmName.substringAfterLast('.')

fun ScheduledExecutorService.scheduleAtFixedRate(initialDelay: Long, every: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, op: () -> Unit) = this.scheduleAtFixedRate(op, initialDelay, every, unit)

fun ScheduledExecutorService.schedule(delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, op: () -> Unit) = this.schedule(op, delay, unit)

fun <T : Any> ObjectMapper.tryReadValue(src: ByteArray, klass: KClass<T>): T? {
    try {
        return this.readValue(src, klass.java)
    } catch (jsonProcessing: JsonProcessingException) {
        return null
    } catch (jsonMapping: JsonMappingException) {
        return null
    } catch (jsonParsing: JsonParseException) {
        return null
    }
}

fun <T : Any> ObjectMapper.tryReadValue(src: InputStream, klass: KClass<T>): T? {
    try {
        return this.readValue(src, klass.java)
    } catch (jsonProcessing: JsonProcessingException) {
        return null
    } catch (jsonMapping: JsonMappingException) {
        return null
    } catch (jsonParsing: JsonParseException) {
        return null
    }
}