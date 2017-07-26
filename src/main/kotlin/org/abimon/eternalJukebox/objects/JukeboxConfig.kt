package org.abimon.eternalJukebox.objects

data class JukeboxConfig(
        val port: Int = 8080,

        val webRoot: String = "web",

        val spotifyClient: String? = null,
        val spotifySecret: String? = null,

        val disabledAPIs: Array<String> = emptyArray(),

        val storageType: String = "LOCAL",
        val storageOptions: Map<String, Any?> = emptyMap(),

        val audioSourceType: String = "YOUTUBE",
        val audioSourceOptions: Map<String, Any?> = emptyMap()
)