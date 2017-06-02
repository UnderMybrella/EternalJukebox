package org.abimon.eternalJukebox.objects

data class EternalUser(
        val id: String,
        var accessToken: String,
        val googleID: String,
        val googleAccessToken: String,
        val googleRefreshToken: String,
        var expiresAt: Long
)