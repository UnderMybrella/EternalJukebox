package org.abimon.eternalJukebox.objects

data class JukeboxAccount(
        val eternalID: String,
        val googleID: String,
        var googleAccessToken: String?,
        var googleRefreshToken: String?,
        val eternalAccessToken: String
)