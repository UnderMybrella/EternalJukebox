package dev.eternalbox.eternaljukebox.data

object WebApiResponseCodes {
    //4xx
    const val ROUTE_NOT_FOUND = HttpResponseCodes.NOT_FOUND
    const val METHOD_NOT_ALLOWED = HttpResponseCodes.METHOD_NOT_ALLOWED

    //5xx
    const val NOT_IMPLEMENTED = HttpResponseCodes.NOT_IMPLEMENTED
    const val NO_CONFIGURE_FILE = 5100
}