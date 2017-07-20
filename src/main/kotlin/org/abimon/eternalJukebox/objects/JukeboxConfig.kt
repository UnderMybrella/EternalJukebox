package org.abimon.eternalJukebox.objects

data class JukeboxConfig(
        val port: Int = 8080,

        val storageType: String = "LOCAL",
        val storageOptions: Map<String, Any?> = emptyMap()
)