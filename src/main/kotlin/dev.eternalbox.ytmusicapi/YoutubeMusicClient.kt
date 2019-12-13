package dev.eternalbox.ytmusicapi

data class YoutubeMusicClient(
    val clientName: String,
    val clientVersion: String,
    val hl: String,
    val gl: String,
    val experimentIds: Array<String>,
    val experimentsToken: String,
    val utfOffsetMinutes: Int,
    val locationInfo: YoutubeMusicLocationInfo,
    val musicAppInfo: YoutubeMusicAppInfo
) {
    companion object {
        val DEFAULT = YoutubeMusicClient(
            "WEB_REMIX",
            "0.1",
            "en",
            "AU",
            emptyArray(),
            "",
            660,
            YoutubeMusicLocationInfo.UNSUPPORTED,
            YoutubeMusicAppInfo.DEFAULT
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as YoutubeMusicClient

        if (clientName != other.clientName) return false
        if (clientVersion != other.clientVersion) return false
        if (hl != other.hl) return false
        if (gl != other.gl) return false
        if (!experimentIds.contentEquals(other.experimentIds)) return false
        if (experimentsToken != other.experimentsToken) return false
        if (utfOffsetMinutes != other.utfOffsetMinutes) return false
        if (locationInfo != other.locationInfo) return false
        if (musicAppInfo != other.musicAppInfo) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientName.hashCode()
        result = 31 * result + clientVersion.hashCode()
        result = 31 * result + hl.hashCode()
        result = 31 * result + gl.hashCode()
        result = 31 * result + experimentIds.contentHashCode()
        result = 31 * result + experimentsToken.hashCode()
        result = 31 * result + utfOffsetMinutes
        result = 31 * result + locationInfo.hashCode()
        result = 31 * result + musicAppInfo.hashCode()
        return result
    }
}

data class YoutubeMusicLocationInfo(val locationPermissionAuthorizationStatus: String) {
    companion object {
        val UNSUPPORTED = YoutubeMusicLocationInfo("LOCATION_PERMISSION_AUTHORIZATION_STATUS_UNSUPPORTED")
    }
}

data class YoutubeMusicAppInfo(
    val musicActivityMasterSwitch: String,
    val musicLocationMasterSwitch: String,
    val pwaInstallabilityStatus: String
) {
    companion object {
        val DEFAULT = YoutubeMusicAppInfo(
            "MUSIC_ACTIVITY_MASTER_SWITCH_INDETERMINATE",
            "MUSIC_LOCATION_MASTER_SWITCH_INDETERMINATE",
            "PWA_INSTALLABILITY_STATUS_UNKNOWN"
        )
    }
}