package org.abimon.eternalJukebox

import com.github.kittinunf.fuel.core.Request
import java.io.InputStream
import java.io.OutputStream
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.safeCast
import kotlin.reflect.jvm.isAccessible

//fun Request.source(data: InputStream, size: Long = data.available().toLong()): Request {
//    bodyCallback = body@ { request, outputStream, totalLength ->
//        var contentLength = 0L
//        val progressCallback: ((Long, Long) -> Unit)? = request.progressCallback
//        outputStream.apply {
//            //input file data
//            if (outputStream != null) {
//                data.use { it.copyWithProgress(outputStream, 1024) { writtenBytes -> progressCallback?.invoke(contentLength + writtenBytes, totalLength) } }
//            }
//
//            contentLength += size
//        }
//
//        progressCallback?.invoke(contentLength, totalLength)
//        return@body contentLength
//    }
//
//    return this
//}

val taskRequestClass = Class.forName("com.github.kittinunf.fuel.core.requests.TaskRequest")
val taskRequestProperty = Request::class["taskRequest", taskRequestClass]

val UPLOAD_TASK = Class.forName("com.github.kittinunf.fuel.core.requests.UploadTaskRequest").kotlin
val progressCallbackProperty: KProperty<((Long, Long) -> Unit)?> = UPLOAD_TASK["progressCallback"]

val Request.progressCallback: ((Long, Long) -> Unit)?
    get() {
        val task = UPLOAD_TASK.safeCast(taskRequestProperty.call(this)) ?: return null
        return progressCallbackProperty.call(task)
    }

@Suppress("UNCHECKED_CAST", "UNUSED_PARAMETER")
operator fun <C: Any, T> KClass<C>.get(property: String, returnClass: Class<T>): KProperty<T> {
    val field = this.declaredMemberProperties.firstOrNull { field -> field.name == property } ?: throw NoSuchFieldException(property)
    field.isAccessible = true
    return field as KProperty<T>
}

@Suppress("UNCHECKED_CAST")
operator fun <C: Any, T> KClass<C>.get(property: String): KProperty<T> {
    val field = this.declaredMemberProperties.firstOrNull { field -> field.name == property } ?: throw NoSuchFieldException(property)
    field.isAccessible = true
    return field as KProperty<T>
}

fun Request.bearer(token: String): Request = header("Authorization" to "Bearer $token")

fun InputStream.copyWithProgress(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, progress: ((Long) -> Unit)?): Long {
    var bytesCopied = 0L
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        progress?.invoke(bytesCopied)
        bytes = read(buffer)
    }
    return bytesCopied
}