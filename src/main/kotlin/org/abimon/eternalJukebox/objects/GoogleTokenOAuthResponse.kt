package org.abimon.eternalJukebox.objects

data class GoogleTokenOAuthResponse(
        val access_token: String,
        val refresh_token: String?,
        val expires_in: Int,
        val token_type: String,
        val id_token: String?
)