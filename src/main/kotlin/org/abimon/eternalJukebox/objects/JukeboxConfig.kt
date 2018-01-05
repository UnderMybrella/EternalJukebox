package org.abimon.eternalJukebox.objects

data class JukeboxConfig(
        val port: Int = 8080,

        val webRoot: String = "web",

        val visitorSecretSize: Int = 8192,

        val spotifyClient: String? = null,
        val spotifySecret: String? = null,

        val disable: Map<String, Boolean> = emptyMap(),

        val storageType: EnumStorageSystem = EnumStorageSystem.LOCAL,
        val storageOptions: Map<String, Any?> = emptyMap(),

        val audioSourceType: EnumAudioSystem = EnumAudioSystem.YOUTUBE,
        val audioSourceOptions: Map<String, Any?> = emptyMap(),

        val analyticsStorageType: EnumAnalyticsStorage = EnumAnalyticsStorage.LOCAL,
        val analyticsStorageOptions: Map<String, Any?> = emptyMap(),

        val analyticsProviders: Map<EnumAnalyticsProvider, Map<String, Any?>> = emptyMap(),

        val usageWritePeriod: Long = 1000 * 60
)