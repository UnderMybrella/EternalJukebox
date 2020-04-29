package org.abimon.eternalJukebox.objects

data class JukeboxConfig(
        val baseDomain: String = "http://localhost:8080",
        val port: Int = 8080,

        val webRoot: String = "web",

        val redirects: Map<String, String> = mapOf("/" to "/jukebox_index.html"),

        val visitorSecretSize: Int = 8192,
        val oauthStateSecretSize: Int = 8192,

        val epoch: Long = 1489148833L,

        val spotifyClient: String? = null,
        val spotifySecret: String? = null,

        val googleClient: String? = null,
        val googleSecret: String? = null,

        val disable: Map<String, Boolean> = emptyMap(),

        val storageType: EnumStorageSystem = EnumStorageSystem.LOCAL,
        val storageOptions: Map<String, Any?> = emptyMap(),

        val audioSourceType: EnumAudioSystem? = null,
        val audioSourceOptions: Map<String, Any?> = emptyMap(),

        val analyticsStorageType: EnumAnalyticsStorage = EnumAnalyticsStorage.LOCAL,
        val analyticsStorageOptions: Map<String, Any?> = emptyMap(),

        val analyticsProviders: Map<EnumAnalyticsProvider, Map<String, Any?>> = emptyMap(),

        val databaseType: EnumDatabaseType = EnumDatabaseType.H2,
        val databaseOptions: Map<String, Any?> = emptyMap(),

        val usageWritePeriod: Long = 1000 * 60,

        val workerExecuteTime: Long = 90L * 1000 * 1000 * 1000,

        val printConfig: Boolean = false,

        val logFiles: Map<String, String?> = emptyMap(),

        val hikariBatchTimeBetweenUpdatesMs: Long = 5 * 60_000,
        val hikariBatchShortIDUpdateTimeMs: Long = 5_000,

        val maximumShortIDCacheSize: Long = 10_000,
        val shortIDCacheStayDurationMinutes: Int = 10,
        val maximumOverridesCacheSize: Long = 1_000,
        val overridesCacheStayDurationMinutes: Int = 10,
        val maximumJukeboxInfoCacheSize: Long = 1_000,
        val jukeboxInfoCacheStayDurationMinutes: Int = 10,
        val locationsCacheStayDurationMinutes: Int = 5,
        val maximumLocationCacheSize: Long = 1_000
)