package org.abimon.eternalJukebox.objects

data class GoogleUser(
        val displayName: String,
        val id: String,
        val image: GoogleUserImage
)

data class GoogleUserImage(
        val url: String,
        val isDefault: Boolean
)