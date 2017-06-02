package org.abimon.eternalJukebox.objects

import java.util.*

data class GoogleToken(
        val access_token: String,
        val expires_in: Long,
        val refresh_token: Optional<String>
)