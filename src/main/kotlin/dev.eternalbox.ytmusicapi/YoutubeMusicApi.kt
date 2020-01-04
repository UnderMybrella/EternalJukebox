package dev.eternalbox.ytmusicapi

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.asResult
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts

class YoutubeMusicApi(
    var userAgent: String = DEFAULT_USER_AGENT,
    var googleVisitorID: String = DEFAULT_GOOGLE_VISITOR_ID,
    var youtubeClientName: String = DEFAULT_YOUTUBE_CLIENT_NAME,
    var youtubeClientVersion: String = DEFAULT_YOUTUBE_CLIENT_VERSION,
    var youtubePageCL: String = DEFAULT_YOUTUBE_PAGE_CL,
    var youtubePageLabel: String = DEFAULT_YOUTUBE_PAGE_LABEL,
    var apiKey: String = DEFAULT_API_KEY
) {
    companion object {
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:70.0) Gecko/20100101 Firefox/70.0"
        const val DEFAULT_GOOGLE_VISITOR_ID: String = "CgtoOHFHaDFDYmZtMCiVp4PvBQ%3D%3D"
        const val DEFAULT_YOUTUBE_CLIENT_NAME: String = "67"
        const val DEFAULT_YOUTUBE_CLIENT_VERSION: String = "0.1"
        const val DEFAULT_YOUTUBE_PAGE_CL: String = "281069543"
        const val DEFAULT_YOUTUBE_PAGE_LABEL: String = "youtube.music.web.client_20191118_00_RC00"
        const val DEFAULT_API_KEY: String = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

        const val API_BASE_URL: String = "https://music.youtube.com/youtubei/v1"
        const val API_SEARCH_URL: String = "$API_BASE_URL/search"

        @ExperimentalContracts
        operator fun invoke(jukebox: EternalJukebox): YoutubeMusicApi = YoutubeMusicApi()
    }

    suspend fun search(query: String): JukeboxResult<YoutubeMusicSearchResponse> =
        Fuel.post(API_SEARCH_URL, listOf("alt" to "json", "key" to apiKey))
            .jsonBody(
                withContext(Dispatchers.IO) { JSON_MAPPER.writeValueAsString(YoutubeMusicSearchRequest.default(query)) }
            )
            .header("Host", "music.youtube.com")
            .header("User-Agent", userAgent)
            .header("X-Goog-Visitor-Id", googleVisitorID)
            .header("X-YouTube-Client-Name", youtubeClientName)
            .header("X-YouTube-Client-Version", youtubeClientVersion)
            .header("X-YouTube-Page-CL", youtubePageCL)
            .header("X-YouTube-Page-Label", youtubePageLabel)
//            .header("X-YouTube-Utc-Offset", 660)
            .header("Origin", "https://music.youtube.com")
            .header("DNT", 1)
            .header("Referer", "https://music.youtube.com/")
            .awaitByteArrayResult()
            .asResult()
//            .awaitString()
}