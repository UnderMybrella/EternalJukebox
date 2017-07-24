package org.abimon.eternalJukebox.objects

import java.time.Duration
import java.time.ZonedDateTime

data class YoutubeSearchResults(
        val kind: String,
        val etag: String,
        val nextPageToken: String,
        val regionCode: String,
        val pageInfo: YoutubePageInfo,
        val items: List<YoutubeSearchItem>
)

data class YoutubePageInfo(
        val totalResults: Long,
        val resultsPerPage: Int
)

data class YoutubeID(
        val kind: String,
        val videoId: String
)

data class YoutubeVideoThumbnail(
        val url: String,
        val width: Int,
        val height: Int
)

data class YoutubeVideo(
        val publishedAt: ZonedDateTime,
        val channelId: String,
        val title: String,
        val description: String,
        val thumbnails: Map<String, YoutubeVideoThumbnail>,
        val channelTitle: String,
        val liveBroadcastContent: String
)

data class YoutubeSearchItem(
        val kind: String,
        val etag: String,
        val id: YoutubeID,
        val snippet: YoutubeVideo
)

data class YoutubeContentResults(
        val kind: String,
        val etag: String,
        val pageInfo: YoutubePageInfo,
        val items: List<YoutubeContentItem>
)

data class YoutubeContentItem(
        val kind: String,
        val etag: String,
        val id: String,
        val contentDetails: YoutubeContentDetails,
        val snippet: YoutubeVideo
)

data class YoutubeContentDetails(
        val duration: Duration,
        val dimension: String,
        val definition: String,
        val caption: String,
        val licensedContent: Boolean,
        val projection: String
)