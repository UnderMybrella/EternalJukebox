package dev.eternalbox.eternaljukebox.data

object WebApiResponseCodes {
    const val FLAG_SHIFT = 4
    //4xx
    const val HTTP_CLIENT_ERROR_SHIFT = 400
    const val HTTP_CLIENT_ERROR_MASK = 1 shl 0 //1
    const val ROUTE_NOT_FOUND = ((HttpResponseCodes.NOT_FOUND  - HTTP_CLIENT_ERROR_SHIFT) shl FLAG_SHIFT) or HTTP_CLIENT_ERROR_MASK
    const val METHOD_NOT_ALLOWED = ((HttpResponseCodes.METHOD_NOT_ALLOWED - HTTP_CLIENT_ERROR_SHIFT) shl FLAG_SHIFT) or HTTP_CLIENT_ERROR_MASK

    const val JUKEBOX_CLIENT_ERROR_MASK = 1 shl 1 //2
    const val INVALID_ANALYSIS_SERVICE = (0 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val INVALID_ANALYSIS_DATA = (1 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val INVALID_UPLOADED_ANALYSIS = (2 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK

    const val ANALYSIS_MISSING_BARS = (10 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val ANALYSIS_MISSING_SEGMENTS = (11 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val ANALYSIS_MISSING_BEATS = (12 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val ANALYSIS_MISSING_SECTIONS = (13 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK
    const val ANALYSIS_MISSING_TATUMS = (14 shl FLAG_SHIFT) or JUKEBOX_CLIENT_ERROR_MASK

    //5xx
    const val HTTP_SERVER_ERROR_SHIFT = 500
    const val HTTP_SERVER_ERROR_MASK = 1 shl 2 //4
    const val NOT_IMPLEMENTED = ((HttpResponseCodes.NOT_IMPLEMENTED - HTTP_SERVER_ERROR_SHIFT) shl FLAG_SHIFT) or HTTP_SERVER_ERROR_MASK

    const val JUKEBOX_SERVER_ERROR_MASK = 1 shl 3 //8
    const val NO_ANALYSIS_PROVIDER = (0 shl FLAG_SHIFT) or JUKEBOX_SERVER_ERROR_MASK
    const val ANALYSIS_NOT_STORED = (1 shl FLAG_SHIFT) or JUKEBOX_SERVER_ERROR_MASK

    const val NO_CONFIGURE_FILE = (200 shl FLAG_SHIFT) or JUKEBOX_SERVER_ERROR_MASK

    infix fun isHttpClientResponseCode(responseCode: Int) = responseCode and HTTP_CLIENT_ERROR_MASK == HTTP_CLIENT_ERROR_MASK
    infix fun isJukeboxClientResponseCode(responseCode: Int) = responseCode and JUKEBOX_CLIENT_ERROR_MASK == JUKEBOX_CLIENT_ERROR_MASK
    infix fun isHttpServerResponseCode(responseCode: Int) = responseCode and HTTP_SERVER_ERROR_MASK == HTTP_SERVER_ERROR_MASK
    infix fun isJukeboxServerResponseCode(responseCode: Int) = responseCode and JUKEBOX_SERVER_ERROR_MASK == JUKEBOX_SERVER_ERROR_MASK

    infix fun asHttpResponseCode(responseCode: Int) = responseCode shr FLAG_SHIFT
}