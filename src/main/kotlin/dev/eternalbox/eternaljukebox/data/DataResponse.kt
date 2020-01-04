package dev.eternalbox.eternaljukebox.data

import java.io.InputStream

sealed class DataResponse {
    data class ExternalUrl(val url: String, val redirectCode: Int = HttpResponseCodes.SEE_OTHER, val localData: DataResponse? = null): DataResponse()
    data class DataSource(val source: () -> InputStream, val contentType: String, val size: Long): DataResponse()
    data class Data(val data: ByteArray, val contentType: String): DataResponse()
}