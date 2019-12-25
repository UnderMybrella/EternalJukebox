package dev.eternalbox.eternaljukebox.data

object WebApiResponseCodes {
    const val ERROR_TYPE_MASK_BITS = 3
    const val ERROR_TYPE_BITS = 16
    const val ERROR_TYPE_SHIFT = (ERROR_TYPE_BITS - ERROR_TYPE_MASK_BITS)
    const val ERROR_TYPE_MASK = ((1 shl ERROR_TYPE_MASK_BITS) - 1) shl ERROR_TYPE_SHIFT
    const val ERROR_MASK = ERROR_TYPE_MASK.inv()

    //4xx
    const val HTTP_CLIENT_ERROR_SHIFT = 400
    const val HTTP_CLIENT_ERROR_TYPE = 1 shl ERROR_TYPE_SHIFT
    const val NOT_FOUND =
        (HttpResponseCodes.NOT_FOUND - HTTP_CLIENT_ERROR_SHIFT) or HTTP_CLIENT_ERROR_TYPE
    const val METHOD_NOT_ALLOWED =
        (HttpResponseCodes.METHOD_NOT_ALLOWED - HTTP_CLIENT_ERROR_SHIFT) or HTTP_CLIENT_ERROR_TYPE

    const val JUKEBOX_CLIENT_ERROR_TYPE = 1 shl 1 //2
    const val INVALID_ANALYSIS_SERVICE = 0 or JUKEBOX_CLIENT_ERROR_TYPE
    const val INVALID_ANALYSIS_DATA = 1 or JUKEBOX_CLIENT_ERROR_TYPE
    const val INVALID_UPLOADED_ANALYSIS = 2 or JUKEBOX_CLIENT_ERROR_TYPE

    const val ANALYSIS_MISSING_BARS = 10 or JUKEBOX_CLIENT_ERROR_TYPE
    const val ANALYSIS_MISSING_SEGMENTS = 11 or JUKEBOX_CLIENT_ERROR_TYPE
    const val ANALYSIS_MISSING_BEATS = 12 or JUKEBOX_CLIENT_ERROR_TYPE
    const val ANALYSIS_MISSING_SECTIONS = 13 or JUKEBOX_CLIENT_ERROR_TYPE
    const val ANALYSIS_MISSING_TATUMS = 14 or JUKEBOX_CLIENT_ERROR_TYPE

    const val INVALID_AUDIO_SERVICE = 20 or JUKEBOX_CLIENT_ERROR_TYPE

    //5xx
    const val HTTP_SERVER_ERROR_SHIFT = 500
    const val HTTP_SERVER_ERROR_TYPE = 1 shl 2 //4
    const val NOT_IMPLEMENTED =
        (HttpResponseCodes.NOT_IMPLEMENTED - HTTP_SERVER_ERROR_SHIFT) or HTTP_SERVER_ERROR_TYPE

    const val JUKEBOX_SERVER_ERROR_TYPE = 1 shl 3 //8
    const val NO_ANALYSIS_PROVIDER = 0 or JUKEBOX_SERVER_ERROR_TYPE
//    const val ANALYSIS_NOT_STORED = 1 or JUKEBOX_SERVER_ERROR_TYPE

    const val NO_AUDIO_PROVIDER = 10 or JUKEBOX_SERVER_ERROR_TYPE
//    const val AUDIO_NOT_STORED = 11 or JUKEBOX_SERVER_ERROR_TYPE

    const val NO_CONFIGURE_FILE = 200 or JUKEBOX_SERVER_ERROR_TYPE

    infix fun isHttpClientResponseCode(responseCode: Int) =
        responseCode and HTTP_CLIENT_ERROR_TYPE == HTTP_CLIENT_ERROR_TYPE

    infix fun isJukeboxClientResponseCode(responseCode: Int) =
        responseCode and JUKEBOX_CLIENT_ERROR_TYPE == JUKEBOX_CLIENT_ERROR_TYPE

    infix fun isHttpServerResponseCode(responseCode: Int) =
        responseCode and HTTP_SERVER_ERROR_TYPE == HTTP_SERVER_ERROR_TYPE

    infix fun isJukeboxServerResponseCode(responseCode: Int) =
        responseCode and JUKEBOX_SERVER_ERROR_TYPE == JUKEBOX_SERVER_ERROR_TYPE

    infix fun asResponseCode(responseCode: Int) = responseCode and ERROR_MASK
    infix fun getHttpStatusCode(statusCode: Int) = when {
        WebApiResponseCodes isHttpClientResponseCode statusCode -> (WebApiResponseCodes asResponseCode statusCode) + HTTP_CLIENT_ERROR_SHIFT
        WebApiResponseCodes isHttpServerResponseCode statusCode -> (WebApiResponseCodes asResponseCode statusCode) + HTTP_SERVER_ERROR_SHIFT
        WebApiResponseCodes isJukeboxClientResponseCode statusCode -> 400
        WebApiResponseCodes isJukeboxServerResponseCode statusCode -> 500
        else -> 500
    }
}