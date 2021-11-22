package dev.eternalbox.audio.ytmsearch

import dev.eternalbox.common.jukebox.EternalboxTrackDetails
import dev.eternalbox.common.utils.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.compression.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class YtmSearchApi(private val client: HttpClient) {
    private inline fun String.parseDuration(): Duration? {
        val durations = split(':')
            .map { it.toIntOrNull() ?: return null }

        return when (durations.size) {
            0 -> null
            1 -> durations[0].seconds
            2 -> durations[0].minutes + durations[1].seconds
            3 -> durations[0].hours + durations[1].minutes + durations[2].seconds
            else -> throw IllegalStateException("Unknown duration ${durations.size} for $this")
        }
    }

    /** Adapted from https://github.com/spotDL/spotify-downloader/blob/master/spotdl/providers/ytm_provider.py under the MIT license*/
    suspend fun fromEternalboxTrack(track: EternalboxTrackDetails): YoutubeMusicSearchResult? {
        try {
//            spotifyToYoutubeData.getIfPresent(songID)?.let { return it }
//            logger.trace("Receiving client request for audio {} -> sending to track channel", songID)
//            val track = SpotifyApi().getTrackInfo(songID)

//            val trackName = track.getValue("name").jsonPrimitive.content
//            val trackAlbum = track.getValue("album").jsonObject.getValue("name").jsonPrimitive.content
//            val trackArtists = track.getValue("artists").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }

            val trackIsrc = track.isrc
            val trackDuration = track.durationMs.milliseconds
            if (trackIsrc != null) {
                val result = searchYoutubeMusic(trackIsrc)
                    ?.firstOrNull()

                if (result != null) {
                    val nameMatch = FuzzySearch.partialRatio(result.songTitle, track.name) >= 80
                    val albumMatch = result.songAlbum?.let { resultAlbum ->
                        track.albumName?.let { trackAlbum ->
                            FuzzySearch.partialRatio(resultAlbum, trackAlbum) >= 80
                        }
                    } ?: false

                    val delta = result.songDuration?.minus(trackDuration)?.toLong(DurationUnit.SECONDS) ?: 0
                    val nonMatchValue = (delta * delta) / trackDuration.toLong(DurationUnit.SECONDS) * 100
                    val timeMatch = (100 - nonMatchValue) >= 90

                    //Observing some weird issues with the YTM cache? I think, so we're defaulting to a 2 of 3 match against the name, album, and time.
                    if (nameMatch) {
                        if (albumMatch || timeMatch) return result
                    } else if (albumMatch && timeMatch) {
                        return result
                    }
                }
            }

            val songTitle = buildString {
                track.artists.joinTo(this, ", ")
                append(" - ")
                append(track.name)
            }

            return searchYoutubeMusic(songTitle)
                ?.mapNotNull { result ->
                    /**
                    ! If there are no common words b/w the spotify and YouTube Music name, the song
                    ! is a wrong match (Like Ruelle - Madness being matched to Ruelle - Monster, it
                    ! happens without this conditional)

                    ! most song results on youtube go by $artist - $song_name, so if the spotify name
                    ! has a '-', this function would return True, a common '-' is hardly a 'common
                    ! word', so we get rid of it. Lower-caseing all the inputs is to get rid of the
                    ! troubles that arise from pythons handling of differently cased words, i.e.
                    ! 'Rhino' == 'rhino' is false though the word is same... so we lower-case both
                    ! sentences and replace any hypens(-)
                     */
                    val commonWords = track.name.replace('-', ' ')
                        .split(' ')
                        .filter { str -> str.isNotBlank() && result.songTitle.contains(str.trim(), true) }

                    if (commonWords.isEmpty()) return@mapNotNull null


                    /**
                    Find artist match
                    ! match  = (no of artist names in result) / (no. of artist names on spotify) * 100
                     */
                    var artist_match_number = 0

                    // ! we use fuzzy matching because YouTube spellings might be mucked up
                    result.songArtist?.let { resultArtist ->
                        track.artists.forEach { artist ->
                            //TODO: Double check to see if we need something equivalent to unidecode
                            if (FuzzySearch.partialRatio(artist, resultArtist) >= 85) artist_match_number++
                        }
                    }

                    if (artist_match_number == 0) return@mapNotNull null

                    //artist_match = (artist_match_number / len(song_artists)) * 100
                    val artistMatch = (artist_match_number / track.artists.size.toFloat()) * 100
                    val nameMatch = FuzzySearch.partialRatio(result.songTitle, track.name)

                    /**
                    skip results with name match of 0, these are obviously wrong
                    but can be identified as correct later on due to other factors
                    such as time_match or artist_match
                     */
                    if (nameMatch == 0) return@mapNotNull null

                    /**
                    Find album match
                    We assign an arbitrary value of 0 for album match in case of video results
                    from YouTube Music
                     */

                    val album = result.songAlbum
                    val albumMatch = if (album == null || track.albumName == null) 0 else FuzzySearch.partialRatio(album.lowercase(), track.albumName!!.lowercase())

                    /**
                    Find duration match
                    time match = 100 - (delta(duration)**2 / original duration * 100)
                    difference in song duration (delta) is usually of the magnitude of a few
                    seconds, we need to amplify the delta if it is to have any meaningful impact
                    wen we calculate the avg match value
                     */

                    val delta = result.songDuration?.minus(trackDuration)?.toLong(DurationUnit.SECONDS) ?: 0
                    val nonMatchValue = (delta * delta) / trackDuration.toLong(DurationUnit.SECONDS) * 100
                    val timeMatch = 100 - nonMatchValue

                    /**
                     * if album is None:
                    # Don't add album_match to average_match if song_name == result and
                    # result album name != song_album_name
                    average_match = (artist_match + name_match + time_match) / 3
                    elif (
                    _match_percentage(album.lower(), result["name"].lower()) > 95
                    and album.lower() != song_album_name.lower()
                    ):
                    average_match = (artist_match + name_match + time_match) / 3
                    # Add album to average_match if song_name == result album
                    # and result album name == song_album_name
                    else:
                    average_match = (
                    artist_match + album_match + name_match + time_match
                    ) / 4
                     */

                    val averageMatch = if (album == null || track.albumName == null)
                        (artistMatch + nameMatch + timeMatch) / 3.0
                    else if (FuzzySearch.partialRatio(album.lowercase(), result.songTitle) > 95 && !album.equals(track.albumName!!, true))
                        (artistMatch + nameMatch + timeMatch) / 3.0
                    else
                        (artistMatch + albumMatch + nameMatch + timeMatch) / 4.0

                    Pair(result, averageMatch)
                }
                ?.maxByOrNull(Pair<YoutubeMusicSearchResult, Double>::second)
                ?.takeIf { (_, accuracy) -> accuracy >= 80 }
                ?.first
        } catch (th: Throwable) {
            th.printStackTrace()
            throw th
        }
    }

    suspend fun searchYoutubeMusic(query: String): List<YoutubeMusicSearchResult>? {
        //Get cookies

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0"

//        GET / HTTP/2
//        Host: music.youtube.com
//        User-Agent: Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0
//        Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/\*;q=0.8
//        Accept-Language: en-US,en;q=0.5
//        Accept-Encoding: gzip, deflate, br
//        Upgrade-Insecure-Requests: 1
//        Connection: keep-alive
        val cookieResponse = client.get<HttpResponse>("https://music.youtube.com/") {
            header("Host", "music.youtube.com")
            userAgent(userAgent)
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.5")
            header("Accept-Encoding", "gzip, deflate")
            header("Upgrade-Insecure-Requests", "1")
            header("Connection", "keep-alive")
        }

        /*
        POST /youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30 HTTP/2
        Host: music.youtube.com
        User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:86.0) Gecko/20100101 Firefox/86.0
        Accept:
        Accept-Language: en-US,en;q=0.5
        Accept-Encoding: gzip, deflate, br
        Content-Type: application/json
        X-Goog-Visitor-Id: Cgt2TkJIbnEyd2owRSjayd-CBg%3D%3D
        X-YouTube-Client-Name: 67
        X-YouTube-Client-Version: 0.1
        X-YouTube-Device: cbr=Firefox&cbrver=86.0&ceng=Gecko&cengver=86.0&cos=Windows&cosver=10.0&cplatform=DESKTOP
        X-YouTube-Page-CL: 362928533
        X-YouTube-Page-Label: youtube.music.web.client_20210315_00_RC00
        X-YouTube-Utc-Offset: 660
        X-YouTube-Time-Zone: Australia/Sydney
        X-YouTube-Ad-Signals: dt=1616372953579&flash=0&frm&u_tz=660&u_his=2&u_java&u_h=1440&u_w=2560&u_ah=1400&u_aw=2560&u_cd=24&u_nplug&u_nmime&bc=31&bih=639&biw=2543&brdim=-8%2C-8%2C-8%2C-8%2C2560%2C0%2C2576%2C1416%2C2560%2C639&vis=1&wgl=true&ca_type=image
        Content-Length: 1186
        Origin: https://music.youtube.com
        DNT: 1
        Connection: keep-alive
        Referer: https://music.youtube.com/
        Cookie: YSC=yn-kJWBMhC4; VISITOR_INFO1_LIVE=vNBHnq2wj0E; PREF=volume=100
        Pragma: no-cache
        Cache-Control: no-cache
        TE: Trailers
        */

        /*
        POST /youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30 HTTP/2
        Host: music.youtube.com
        User-Agent: Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0
        Accept:
        Accept-Language: en-US,en;q=0.5
        Accept-Encoding: gzip, deflate, br
        Referer: https://music.youtube.com/
        Content-Type: application/json
        X-Goog-Visitor-Id: CgtGdktCNi1TbzhGdyjk19-CBg%3D%3D
        X-YouTube-Client-Name: 67
        X-YouTube-Client-Version: 0.1
        X-YouTube-Device: cbr=Firefox&cbrver=78.0&ceng=Gecko&cengver=78.0&cos=Windows&cosver=10.0&cplatform=DESKTOP
        X-YouTube-Page-CL: 362928533
        X-YouTube-Page-Label: youtube.music.web.client_20210315_00_RC00
        X-YouTube-Utc-Offset: 0
        X-YouTube-Time-Zone: UTC
        X-YouTube-Ad-Signals: dt=1616374760700&flash=0&frm&u_tz&u_his=2&u_java&u_h=1000&u_w=1000&u_ah=1000&u_aw=1000&u_cd=24&u_nplug&u_nmime&bc=29&bih=1000&biw=983&brdim=0%2C0%2C0%2C0%2C1000%2C0%2C1000%2C1000%2C1000%2C1000&vis=1&wgl=true&ca_type=image
        Content-Length: 1185
        Origin: https://music.youtube.com
        Connection: keep-alive
        Cookie: YSC=0gCkLGSJRFg; VISITOR_INFO1_LIVE=FvKB6-So8Fw; CONSENT=PENDING+878; _gcl_au=1.1.1930312689.1616374761; PREF=volume=100
        TE: Trailers
        */

        val searchResults = client.post<JsonObject>("https://music.youtube.com/youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30") {
            header("Host", "music.youtube.com")
            userAgent(userAgent)
            accept(ContentType.parse("*/*"))
            contentType(ContentType.parse("application/json"))
            header("Accept-Language", "en-US,en;q=0.5")
            header("Accept-Encoding", "gzip, deflate")
//            header("X-Goog-Visitor-Id", "Cgt2TkJIbnEyd2owRSjayd-CBg%3D%3D")
            header("X-YouTube-Client-Name", 67)
            header("X-YouTube-Client-Version", 0.1)
            header("X-YouTube-Device", "cbr=Firefox&cbrver=78.0&ceng=Gecko&cengver=78.0&cos=Windows&cosver=10.0&cplatform=DESKTOP")
//            header("X-YouTube-Page-CL", 362928533)
            header("X-YouTube-Page-Label", "youtube.music.web.client_20210315_00_RC00")
            header("X-YouTube-Utc-Offset", 0)
            header("X-YouTube-Time-Zone", "UTC")
            header(
                //dt=1616374760700&flash=0&frm&u_tz&u_his=2&u_java&u_h=1000&u_w=1000&u_ah=1000&u_aw=1000&u_cd=24&u_nplug&u_nmime&bc=29&bih=1000&biw=983&brdim=0%2C0%2C0%2C0%2C1000%2C0%2C1000%2C1000%2C1000%2C1000&vis=1&wgl=true&ca_type=image
                "X-YouTube-Ad-Signals",
                parametersOf(
                    //Epoch Time
                    "dt" to listOf(System.currentTimeMillis().toString()),
                    //Flash Enabled?
                    "flash" to listOf("0"),
                    //Empty
                    "frm" to emptyList(),
                    // -(new Date().getTimezoneOffset())
                    "u_tz" to listOf("0"),
                    // history.length
                    "u_his" to listOf("2"),
                    //navigator.javaEnabled()
                    "u_java" to emptyList(),
                    //window.screen.height
                    "u_h" to listOf("1000"),
                    //window.screen.width
                    "u_w" to listOf("1000"),
                    //window.screen.availHeight
                    "u_ah" to listOf("1000"),
                    //window.screen.availWidth
                    "u_aw" to listOf("1000"),
                    //window.screen.colorDepth
                    "u_cd" to listOf("24"),
                    //navigator.plugins.length
                    "u_nplug" to emptyList(),
                    //navigator.mimeTypes.length
                    "u_nmime" to emptyList(),
                    //Browser Cookies?
                    "bc" to listOf("29"),
                    //Browser Inner Height
                    "bih" to listOf("1000"),
                    //Browser Inner Width (Seems to be document clientWidth)
                    "biw" to listOf("983"),
                    //browser dimensions??
                    "brdim" to listOf("0,0,0,0,1000,0,1000,1000,1000,1000"),
                    //???
                    "vis" to listOf("1"),
                    //WebGL?
                    "wgl" to listOf("true"),
                    //??
                    "ca_type" to listOf("image")
                ).formUrlEncode()
            )

//            Content - Length: 1186
            header("Origin", "https://music.youtube.com")
            header("DNT", 1)
            header("Connection", "keep-alive")
            header("Referer", "https://music.youtube.com")
//            Cookie: YSC = yn-kJWBMhC4; VISITOR_INFO1_LIVE = vNBHnq2wj0E; PREF = volume = 100
            header("Pragma", "no-cache")
            header("Cache-Control", "no-cache")
            header("TE", "Trailers")

            cookieResponse.setCookie().forEach { (name, value, _, maxAge, expires, domain, path, secure, httpOnly, extensions) ->
                cookie(name, value, maxAge, expires, domain, path, secure, httpOnly, extensions)
            }

            body = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "0.1")
                        put("hl", "en")
                        put("gl", "AT")
                        putJsonArray("experimentIds") {}
                        put("experimentsToken", "")
                        put("browserName", "Firefox")
                        put("browserVersion", "78.0")
                        put("osName", "Windows")
                        put("osVersion", "10.0")
                        put("platform", "DESKTOP")
                        put("utcOffsetMinutes", 0)
                        putJsonObject("locationInfo") {
                            put("locationPermissionAuthorizationStatus", "LOCATION_PERMISSION_AUTHORIZATION_STATUS_UNSUPPORTED")
                        }
                        putJsonObject("musicAppInfo") {
                            put("musicActivityMasterSwitch", "MUSIC_ACTIVITY_MASTER_SWITCH_INDETERMINATE")
                            put("musicLocationMasterSwitch", "MUSIC_LOCATION_MASTER_SWITCH_INDETERMINATE")
                            put("pwaInstallabilityStatus", "PWA_INSTALLABILITY_STATUS_UNKNOWN")
                        }
                    }

                    putJsonObject("capabilities") {}

                    putJsonObject("request") {
                        putJsonArray("internalExperimentFlags") {}
                    }

                    putJsonObject("activePlayers") {}

                    putJsonObject("user") {
                        put("enableSafetyMode", false)
                    }
                }

                put("query", query)
                //Songs only
                put("params", "EgWKAQIIAWoKEAMQBBAJEAUQCg%3D%3D")

//                putJsonObject("suggestStats") {
//                    put("validationStatus", "VALID")
//                    put("parameterValidationStatus", "VALID_PARAMETERS")
//                    put("clientName", "youtube-music")
//                    put("searchMethod", "ENTER_KEY")
//                    put("inputMethod", "KEYBOARD")
//                    put("originalQuery", query)
//                    putJsonArray("availableSuggestions") {}
//                    put("zeroPrefixEnabled", true)
//                    put("firstEditTimeMsec", 522000)
//                    put("lastEditTimeMsec", 525000)
//                }
            }
        }

        var content = searchResults.getJsonObject("contents")
        if ("tabbedSearchResultsRenderer" in content)
            content = content.getJsonObject("tabbedSearchResultsRenderer")
                .getJsonArray("tabs")
                .firstOf<JsonObject>()
                .getJsonObject("tabRenderer")
                .getJsonObject("content")

        return content
            .getJsonObject("sectionListRenderer")
            .getJsonArray("contents")
            .mapAsNotNull { obj: JsonObject -> obj.getJsonObjectOrNull("musicShelfRenderer") }
            .firstOrNull { shelf ->
                shelf.getJsonObjectOrNull("title")
                    ?.getJsonArrayOrNull("runs")
                    ?.firstOfOrNull<JsonObject>()
                    ?.getStringOrNull("text")
                    ?.equals("Songs", true) == true
            }?.getJsonArrayOrNull("contents")
            ?.mapAsNotNull { shelf: JsonObject ->
                val songJson = shelf.getJsonObject("musicResponsiveListItemRenderer")

                val songDetails = songJson
                    .getJsonArray("flexColumns")
                    .mapAs { column: JsonObject ->
                        column.getJsonObject("musicResponsiveListItemFlexColumnRenderer")
                            .getJsonObject("text")
                            .getJsonArray("runs")
                            .mapAs { run: JsonObject -> run.getString("text") }
                    }

                val songTitle = songDetails[0][0]
//                val key = "\\/${System.nanoTime().let { num -> "${num and 0xFFFFFFFF}/${(num shl 24).toString(16)}" }}\\/"
                val songDetailsList = songDetails[1].joinToString("").split("•").map(String::trim)

                val videoID = songJson
                                  .getJsonObjectOrNull("playlistItemData")
                                  ?.getStringOrNull("videoId") ?: return@mapAsNotNull null

                val thumbnails = songJson
                    .getJsonObjectOrNull("thumbnail")
                    ?.getJsonObjectOrNull("musicThumbnailRenderer")
                    ?.getJsonObjectOrNull("thumbnail")
                    ?.getJsonArrayOrNull("thumbnails")
                    ?.mapAsNotNull inner@{ obj: JsonObject ->
                        val url = obj.getStringOrNull("url") ?: return@inner null
                        val width = obj.getIntOrNull("width") ?: return@inner null
                        val height = obj.getIntOrNull("height") ?: return@inner null

                        YoutubeMusicThumbnail(url, width, height)
                    }

                YoutubeMusicSearchResult(
                    videoID, songTitle,
                    songAlbum = songDetailsList.getOrNull(1),
                    songArtist = songDetailsList.getOrNull(0),
                    songDuration = songDetailsList.getOrNull(2)?.parseDuration(),
                    thumbnails ?: emptyList()
                )
            }
    }
}