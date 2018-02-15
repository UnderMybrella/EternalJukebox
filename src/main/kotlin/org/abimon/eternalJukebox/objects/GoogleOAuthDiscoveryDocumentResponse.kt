package org.abimon.eternalJukebox.objects

data class GoogleOAuthDiscoveryDocumentResponse(
        val authorization_endpoint: String,
        val token_endpoint: String,
        val userinfo_endpoint: String
)