package dev.eternalbox.ytmusicapi

data class YoutubeMusicSearchRequest(val context: YoutubeMusicRequestContext, val query: String, val suggestStats: YoutubeMusicSuggestStats) {
    companion object {
        fun default(query: String) = YoutubeMusicSearchRequest(
            YoutubeMusicRequestContext.DEFAULT,
            query,
            YoutubeMusicSuggestStats.default(query)
        )
    }
}