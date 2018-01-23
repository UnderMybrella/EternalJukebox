package org.abimon.eternalJukebox.objects

import org.abimon.visi.io.DataSource
import org.abimon.visi.io.readChunked
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.Channels
import java.util.*

class FuelHTTPDataSource(val url: URL, val userAgent: String) : DataSource {
    constructor(url: URL) : this(url, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")

    override val location: String = url.toExternalForm()

    override val data: ByteArray
        get() = use { stream -> stream.readBytes() }

    override val inputStream: InputStream
        get() {
            val http = url.openConnection() as HttpURLConnection
            http.requestMethod = "GET"
            http.setRequestProperty("User-Agent", userAgent)
            return if (http.responseCode < 400) http.inputStream else http.errorStream
        }

    override val seekableInputStream: InputStream
        get() {
            val http = url.openConnection() as HttpURLConnection
            http.requestMethod = "GET"
            http.setRequestProperty("User-Agent", userAgent)
            val stream = if (http.responseCode < 400) http.inputStream else http.errorStream
            val tmp = File.createTempFile(UUID.randomUUID().toString(), "tmp")
            tmp.deleteOnExit()
            FileOutputStream(tmp).use { out -> stream.use { inStream -> inStream.copyTo(out) } }
            return Channels.newInputStream(RandomAccessFile(tmp, "r").channel)
        }

    override val size: Long
        get() = use { stream ->
            var size = 0L
            stream.readChunked { chunk -> size += chunk.size }
            return@use size
        }
}