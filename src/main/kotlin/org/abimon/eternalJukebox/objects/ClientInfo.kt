package org.abimon.eternalJukebox.objects

import io.vertx.ext.web.RoutingContext

data class ClientInfo(
        val userUID: String
) {
    constructor(context: RoutingContext): this((context.data()[ConstantValues.USER_UID] as? String)!!)
}