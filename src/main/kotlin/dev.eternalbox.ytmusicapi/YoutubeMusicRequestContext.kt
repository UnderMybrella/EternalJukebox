package dev.eternalbox.ytmusicapi

data class YoutubeMusicRequestContext(
    val client: YoutubeMusicClient,
    val capabilities: UnknownJsonObj,
    val request: YoutubeMusicRequest,
    val activePlayers: UnknownJsonObj,
    val user: YoutubeMusicUser
) {
    companion object {
        val DEFAULT = YoutubeMusicRequestContext(
            YoutubeMusicClient.DEFAULT,
            emptyMap(),
            YoutubeMusicRequest.DEFAULT,
            emptyMap(),
            YoutubeMusicUser(false)
        )
    }
}

data class YoutubeMusicRequest(
    val internalExperimentFlags: Array<SimpleEntry>,
    val sessionIndex: UnknownJsonObj
) {
    companion object {
        val DEFAULT = YoutubeMusicRequest(
            arrayOf(
                SimpleEntry("force_music_enable_outertube_tastebuilder_browse", "true"),
                SimpleEntry("force_music_enable_outertube_playlist_detail_browse", "true"),
                SimpleEntry("force_music_enable_outertube_search_suggestions", "true")
            ),
            emptyMap()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as YoutubeMusicRequest

        if (!internalExperimentFlags.contentEquals(other.internalExperimentFlags)) return false

        return true
    }

    override fun hashCode(): Int {
        return internalExperimentFlags.contentHashCode()
    }
}