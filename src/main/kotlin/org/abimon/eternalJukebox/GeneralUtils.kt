package org.abimon.eternalJukebox

import io.vertx.core.json.JsonObject
import org.abimon.visi.collections.copyFrom
import java.io.File
import java.io.Reader
import java.util.*

fun log(msg: String, error: Boolean = false) {
    val there = Thread.currentThread().stackTrace.copyFrom(1).firstOrNull { it.className != "org.abimon.eternalJukebox.GeneralUtilsKt" && !it.className.contains('$') } ?: run {
        if(error)
            System.err.println("[Unknown] $msg")
        else
            println("[Unknown] $msg")
        return@log
    }

    val className = run {
        try{
            return@run Class.forName(there.className).simpleName
        }
        catch(notFound: ClassNotFoundException) { return@run there.className }
    }

    if(error)
        System.err.println("[$className -> ${there.methodName}] $msg")
    else
        println("[$className -> ${there.methodName}] $msg")
}

fun <T> logNull(msg: String, error: Boolean = false): T? {
    log(msg, error)
    return null
}

/**
 * @param task return true if we need to back off
 */
fun exponentiallyBackoff(maximumBackoff: Long, maximumTries: Long, task: () -> Boolean): Boolean {
    if (!task())
        return true

    val rng = Random()

    for (i in 0 until maximumTries) {
        if (!task())
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
        if(exists())
            return action(this)
        else
            return null
    }
    finally {
        delete()
    }
}

fun Reader.useAndFilterLine(predicate: (String) -> Boolean): String? = this.use { reader -> reader.readLines().firstOrNull(predicate) }

fun Reader.useLineByLine(op: (String) -> Unit) = this.use { reader -> reader.readLines().forEach(op) }