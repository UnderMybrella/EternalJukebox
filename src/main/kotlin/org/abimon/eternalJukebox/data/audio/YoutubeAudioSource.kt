package org.abimon.eternalJukebox.data.audio

import com.github.kittinunf.fuel.Fuel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.MediaWrapper
import org.abimon.eternalJukebox.guaranteeDelete
import org.abimon.eternalJukebox.objects.*
import org.abimon.eternalJukebox.useThenDelete
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.FileDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

object YoutubeAudioSource : IAudioSource {
    val apiKey: String?
    val uuid: String
        get() = UUID.randomUUID().toString()
    val format: String
    val command: List<String>

    val logger = LoggerFactory.getLogger("YoutubeAudioSource")

    val mimes = mapOf(
        "m4a" to "audio/m4a",
        "aac" to "audio/aac",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "wav" to "audio/wav"
    )

    val hitQuota = AtomicLong(-1)
    val QUOTA_TIMEOUT = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES)

    override suspend fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? {
        if (apiKey == null)
            return null

        logger.trace("[{}] Attempting to provide audio for {}", clientInfo?.userUID, info.id)

        val artistTitle = getMultiContentDetailsWithKey(
            searchYoutubeWithKey(
                "${info.artist} - ${info.title}",
                10
            ).map { it.id.videoId })
        val artistTitleLyrics = getMultiContentDetailsWithKey(
            searchYoutubeWithKey(
                "${info.artist} - ${info.title} lyrics",
                10
            ).map { it.id.videoId })
        val both = ArrayList<YoutubeContentItem>().apply {
            addAll(artistTitle)
            addAll(artistTitleLyrics)
        }.sortedWith(Comparator { o1, o2 ->
            Math.abs(info.duration - o1.contentDetails.duration.toMillis())
                .compareTo(Math.abs(info.duration - o2.contentDetails.duration.toMillis()))
        })

        val closest = both.firstOrNull()
            ?: return kotlin.run {
                logger.error(
                    "[{}] Searches for both \"{} - {}\" and \"{} - {} lyrics\" turned up nothing",
                    clientInfo?.userUID,
                    info.artist,
                    info.title,
                    info.artist,
                    info.title
                )
                null
            }

        logger.trace(
            "[{}] Settled on {} (https://youtu.be/{})",
            clientInfo?.userUID, closest.snippet.title, closest.id
        )

        val tmpFile = File("$uuid.tmp")
        val tmpLog = File("${info.id}-$uuid.log")
        val ffmpegLog = File("${info.id}-$uuid.log")
        val endGoalTmp = File(tmpFile.absolutePath.replace(".tmp", ".$format"))

        try {
            withContext(Dispatchers.IO) {
                val downloadProcess = ProcessBuilder().command(ArrayList(command).apply {
                    add("https://youtu.be/${closest.id}")
                    add(tmpFile.absolutePath)
                    add(format)
                }).redirectErrorStream(true).redirectOutput(tmpLog).start()

                if (!downloadProcess.waitFor(90, TimeUnit.SECONDS)) {
                    downloadProcess.destroyForcibly().waitFor()
                    logger.error("[{}] Forcibly destroyed the download process for {}", clientInfo?.userUID, closest.id)
                }
            }

            if (!endGoalTmp.exists()) {
                logger.warn(
                    "[{}] {} does not exist, attempting to convert with ffmpeg",
                    clientInfo?.userUID,
                    endGoalTmp
                )

                if (!tmpFile.exists()) {
                    logger.error("[{}] {} does not exist, what happened?", clientInfo?.userUID, tmpFile)
                    return null
                }

                if (MediaWrapper.ffmpeg.installed) {
                    if (!MediaWrapper.ffmpeg.convert(tmpFile, endGoalTmp, ffmpegLog)) {
                        logger.error("[{}] Failed to convert {} to {}", clientInfo?.userUID, tmpFile, endGoalTmp)
                        return null
                    }

                    if (!endGoalTmp.exists()) {
                        logger.error(
                            "[{}] {} does not exist, what happened?",
                            clientInfo?.userUID,
                            endGoalTmp
                        )
                        return null
                    }
                } else {
                    logger.debug("[{}] ffmpeg not installed, nothing we can do", clientInfo?.userUID)
                }
            }

            withContext(Dispatchers.IO) {
                endGoalTmp.useThenDelete {
                    EternalJukebox.storage.store(
                        "${info.id}.$format", EnumStorageType.AUDIO, FileDataSource(it), mimes[format]
                            ?: "audio/mpeg", clientInfo
                    )
                }
            }

            return EternalJukebox.storage.provide("${info.id}.$format", EnumStorageType.AUDIO, clientInfo)
        } finally {
            tmpFile.guaranteeDelete()
            File(tmpFile.absolutePath + ".part").guaranteeDelete()
            withContext(Dispatchers.IO) {
                tmpLog.useThenDelete {
                    EternalJukebox.storage.store(
                        it.name,
                        EnumStorageType.LOG,
                        FileDataSource(it),
                        "text/plain",
                        clientInfo
                    )
                }
                ffmpegLog.useThenDelete {
                    EternalJukebox.storage.store(
                        it.name,
                        EnumStorageType.LOG,
                        FileDataSource(it),
                        "text/plain",
                        clientInfo
                    )
                }
                endGoalTmp.useThenDelete {
                    EternalJukebox.storage.store(
                        "${info.id}.$format", EnumStorageType.AUDIO, FileDataSource(it), mimes[format]
                            ?: "audio/mpeg", clientInfo
                    )
                }
            }
        }
    }

    override fun provideLocation(info: JukeboxInfo, clientInfo: ClientInfo?): URL? {
        val dbLocation = EternalJukebox.database.provideAudioLocation(info.id, clientInfo)

        if (dbLocation != null) {
            logger.trace("[{}] Using cached location for {}", clientInfo?.userUID, info.id)
            return URL(dbLocation)
        }

        if (apiKey == null)
            return null

        logger.trace("[{}] Attempting to provide a location for {}", clientInfo?.userUID, info.id)

        val artistTitle = getMultiContentDetailsWithKey(
            searchYoutubeWithKey(
                "${info.artist} - ${info.title}",
                10
            ).map { it.id.videoId })
        val artistTitleLyrics = getMultiContentDetailsWithKey(
            searchYoutubeWithKey(
                "${info.artist} - ${info.title} lyrics",
                10
            ).map { it.id.videoId })
        val both = ArrayList<YoutubeContentItem>().apply {
            addAll(artistTitle)
            addAll(artistTitleLyrics)
        }.sortedWith(Comparator { o1, o2 ->
            abs(info.duration - o1.contentDetails.duration.toMillis())
                .compareTo(abs(info.duration - o2.contentDetails.duration.toMillis()))
        })

        val closest = both.firstOrNull()
            ?: run {
                logger.error(
                    "[{}] Searches for both \"{} - {}\" and \"{} - {} lyrics\" turned up nothing",
                    clientInfo?.userUID,
                    info.artist,
                    info.title,
                    info.artist,
                    info.title
                )
                return null
            }

        EternalJukebox.database.storeAudioLocation(info.id, "https://youtu.be/${closest.id}", clientInfo)
        return URL("https://youtu.be/${closest.id}")
    }

    fun getContentDetailsWithKey(id: String): YoutubeContentItem? {
        val lastQuota = hitQuota.get()

        if (lastQuota != -1L) {
            if ((Instant.now().toEpochMilli() - lastQuota) < QUOTA_TIMEOUT)
                return null
            hitQuota.set(-1)
        }

        val (_, _, r) = Fuel.get(
                "https://www.googleapis.com/youtube/v3/videos", listOf(
                    "part" to "contentDetails,snippet", "id" to id, "key" to (apiKey
                        ?: return null)
                )
            )
            .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
            .responseString()

        val (result, error) = r

        if (error != null) {
            if (error.response.statusCode == 403) {
                println("Hit quota!")
                hitQuota.set(Instant.now().toEpochMilli())
            }
            return null
        }

        return EternalJukebox.jsonMapper.readValue(result, YoutubeContentResults::class.java).items.firstOrNull()
    }

    fun getMultiContentDetailsWithKey(ids: List<String>): List<YoutubeContentItem> {
        val lastQuota = hitQuota.get()

        if (lastQuota != -1L) {
            if ((Instant.now().toEpochMilli() - lastQuota) < QUOTA_TIMEOUT)
                return emptyList()
            hitQuota.set(-1)
        }

        val (_, _, r) = Fuel.get(
                "https://www.googleapis.com/youtube/v3/videos", listOf(
                    "part" to "contentDetails,snippet", "id" to ids.joinToString(), "key" to (apiKey
                        ?: return emptyList())
                )
            )
            .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
            .responseString()

        val (result, error) = r

        if (error != null) {
            if (error.response.statusCode == 403) {
                println("Hit quota!")
                hitQuota.set(Instant.now().toEpochMilli())
            }
            return emptyList()
        }

        return EternalJukebox.jsonMapper.readValue(result, YoutubeContentResults::class.java).items
    }

    fun searchYoutubeWithKey(query: String, maxResults: Int = 5): List<YoutubeSearchItem> {
        val lastQuota = hitQuota.get()

        if (lastQuota != -1L) {
            if ((Instant.now().toEpochMilli() - lastQuota) < QUOTA_TIMEOUT)
                return emptyList()
            hitQuota.set(-1)
        }

        val (_, _, r) = Fuel.get(
                "https://www.googleapis.com/youtube/v3/search", listOf(
                    "part" to "snippet", "q" to query, "maxResults" to "$maxResults", "key" to (apiKey
                        ?: return emptyList()), "type" to "video"
                )
            )
            .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
            .responseString()

        val (result, error) = r

        if (error != null) {
            if (error.response.statusCode == 403) {
                println("Hit quota!")
                hitQuota.set(Instant.now().toEpochMilli())
            }
            return ArrayList()
        }

        return EternalJukebox.jsonMapper.readValue(result, YoutubeSearchResults::class.java).items
    }

    init {
        apiKey = (EternalJukebox.config.audioSourceOptions["API_KEY"]
            ?: EternalJukebox.config.audioSourceOptions["apiKey"]) as? String
        format = (EternalJukebox.config.audioSourceOptions["AUDIO_FORMAT"]
            ?: EternalJukebox.config.audioSourceOptions["audioFormat"]) as? String ?: "m4a"
        command = ((EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"]
            ?: EternalJukebox.config.audioSourceOptions["audioCommand"]) as? List<*>)?.map { "$it" }
            ?: ((EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"]
                ?: EternalJukebox.config.audioSourceOptions["audioCommand"]) as? String)?.split("\\s+".toRegex())
                    ?: if (System.getProperty("os.name").toLowerCase()
                    .contains("windows")
            ) listOf("yt.bat") else listOf("bash", "yt.sh")

        if (apiKey == null)
            logger.warn(
                "Warning: No API key provided. We're going to scrape the Youtube search page which is a not great thing to do.\nTo obtain an API key, follow the guide here (https://developers.google.com/youtube/v3/getting-started) or over on the EternalJukebox Github page!"
            )
    }
}