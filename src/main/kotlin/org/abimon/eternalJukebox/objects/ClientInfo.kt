package org.abimon.eternalJukebox.objects

import io.vertx.core.http.HttpHeaders
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import java.util.*

data class ClientInfo(
        val userUID: String,
        val authToken: String?,
        val isNewHourly: Boolean = false,
        val remoteAddress: SocketAddress
) {
    constructor(context: RoutingContext) : this(
            (context.data()[ConstantValues.USER_UID] as? String) ?: UUID.randomUUID().toString(),
            context.request().getHeader(HttpHeaders.AUTHORIZATION) ?: context.getCookie(ConstantValues.AUTH_COOKIE_NAME)?.value,
            (context.data()[ConstantValues.HOURLY_UNIQUE_VISITOR] as? Boolean ?: false),
            context.request().connection().remoteAddress()
    )
}