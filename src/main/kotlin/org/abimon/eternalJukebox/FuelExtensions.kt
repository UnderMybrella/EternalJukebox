package org.abimon.eternalJukebox

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.requests.UploadTaskRequest
import com.github.kittinunf.fuel.util.copyTo
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

fun Request.source(data: InputStream, size: Long = data.available().toLong()): Request {
    bodyCallback = body@ { request, outputStream, totalLength ->
        var contentLength = 0L
        val progressCallback: ((Long, Long) -> Unit)? = (request.taskRequest as? UploadTaskRequest)?.progressCallback
        outputStream.apply {
            //input file data
            if (outputStream != null) {
                data.use {
                    it.copyTo(outputStream, 1024) { writtenBytes ->
                        progressCallback?.invoke(contentLength + writtenBytes, totalLength)
                    }
                }
            }

            contentLength += size
        }

        progressCallback?.invoke(contentLength, totalLength)
        return@body contentLength
    }

    return this
}

fun Request.source(file: File): Request = source(FileInputStream(file), file.length())

fun Request.bearer(token: String): Request = header("Authorization" to "Bearer $token")