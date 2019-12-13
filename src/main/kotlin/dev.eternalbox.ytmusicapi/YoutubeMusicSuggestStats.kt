package dev.eternalbox.ytmusicapi

data class YoutubeMusicSuggestStats(
    val validationStatus: String,
    val parameterValidationStatus: String,
    val clientName: String,
    val searchMethod: String,
    val inputMethod: String,
    val originalQuery: String,
    val availableSuggestions: Array<YoutubeMusicSuggestion>
) {
    companion object {
        fun default(query: String) = YoutubeMusicSuggestStats(
            "VALID",
            "VALID_PARAMETERS",
            "youtube-music",
            "ENTER_KEY",
            "KEYBOARD",
            query,
            Array(7) { i -> YoutubeMusicSuggestion(i, 0) }
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as YoutubeMusicSuggestStats

        if (validationStatus != other.validationStatus) return false
        if (parameterValidationStatus != other.parameterValidationStatus) return false
        if (clientName != other.clientName) return false
        if (searchMethod != other.searchMethod) return false
        if (inputMethod != other.inputMethod) return false
        if (originalQuery != other.originalQuery) return false
        if (!availableSuggestions.contentEquals(other.availableSuggestions)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = validationStatus.hashCode()
        result = 31 * result + parameterValidationStatus.hashCode()
        result = 31 * result + clientName.hashCode()
        result = 31 * result + searchMethod.hashCode()
        result = 31 * result + inputMethod.hashCode()
        result = 31 * result + originalQuery.hashCode()
        result = 31 * result + availableSuggestions.contentHashCode()
        return result
    }
}

data class YoutubeMusicSuggestion(val index: Int, val type: Int)