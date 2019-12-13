package dev.eternalbox.eternaljukebox.data

object WebApiResponseMessages {
    //4xx
    const val API_ROUTE_NOT_FOUND = "route_not_found"
    const val API_METHOD_NOT_ALLOWED_FOR_ROUTE = "method_not_allowed_for_route"

    //5xx
    const val API_NOT_IMPLEMENTED = "api_not_implemented"
    const val NO_CONFIGURE_FILE = "no_configure_file"
}