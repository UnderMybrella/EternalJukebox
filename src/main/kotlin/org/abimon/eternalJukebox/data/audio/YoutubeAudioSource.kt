package org.abimon.eternalJukebox.data.audio

import com.github.kittinunf.fuel.Fuel
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.log
import org.abimon.eternalJukebox.logNull
import org.abimon.eternalJukebox.objects.*
import org.abimon.eternalJukebox.useThenDelete
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.FileDataSource
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

object YoutubeAudioSource: IAudioSource {
    val apiKey: String?
    val uuid: String
        get() = UUID.randomUUID().toString()
    val format: String
    val command: List<String>

    val ffmpegInstalled: Boolean
        get() {
            val process = ProcessBuilder().command("ffmpeg", "-version").start()

            process.waitFor(5, TimeUnit.SECONDS)

            return String(process.inputStream.readBytes(), Charsets.UTF_8).startsWith("ffmpeg version")
        }

    override fun provide(info: JukeboxInfo): DataSource? {
        if(apiKey == null)
            return null

        log("Attempting to provide audio for $info")

        val artistTitle = getMultiContentDetailsWithKey(searchYoutubeWithKey("${info.artist} - ${info.title}", 10).map { it.id.videoId })
        val artistTitleLyrics = getMultiContentDetailsWithKey(searchYoutubeWithKey("${info.artist} - ${info.title} lyrics", 10).map { it.id.videoId })
        val both = ArrayList<YoutubeContentItem>().apply {
            addAll(artistTitle)
            addAll(artistTitleLyrics)
        }.sortedWith(Comparator { o1, o2 -> Math.abs(info.duration - o1.contentDetails.duration.toMillis()).compareTo(Math.abs(info.duration - o2.contentDetails.duration.toMillis())) })

        val closest = both.firstOrNull() ?: return logNull("Searches for both \"${info.artist} - ${info.title}\" and \"${info.artist} - ${info.title} lyrics\" turned up nothing")

        log("Settled on ${closest.snippet.title} (https://youtu.be/${closest.id})")

        val tmpFile = File("$uuid.tmp")
        val tmpLog = File("${info.id}-$uuid.log")
        val ffmpegLog = File("${info.id}-$uuid.log")
        val endGoalTmp = File(tmpFile.absolutePath.replace(".tmp", ".$format"))

        try {
            val downloadProcess = ProcessBuilder().command(ArrayList(command).apply {
                add("https://youtu.be/${closest.id}")
                add(tmpFile.absolutePath)
                add(format)
            }).redirectErrorStream(true).redirectOutput(tmpLog).start()

            downloadProcess.waitFor(60, TimeUnit.SECONDS)

            if(!endGoalTmp.exists()) {
                log("$endGoalTmp does not exist, attempting to convert with ffmpeg")

                if(!tmpFile.exists())
                    return logNull("$tmpFile does not exist, what happened?", true)

                if(ffmpegInstalled) {
                    val ffmpegProcess = ProcessBuilder().command("ffmpeg", "-i", tmpFile.absolutePath, endGoalTmp.absolutePath).redirectErrorStream(true).redirectOutput(ffmpegLog).start()

                    ffmpegProcess.waitFor(60, TimeUnit.SECONDS)

                    if(!endGoalTmp.exists())
                        return logNull("$endGoalTmp still does not exist, what happened?", true)
                }
                else
                    return logNull("ffmpeg not installed, nothing we can do", true)
            }

            endGoalTmp.useThenDelete { EternalJukebox.storage.store("${info.id}.$format", EnumStorageType.AUDIO, FileDataSource(it)) }

            return EternalJukebox.storage.provide("${info.id}.$format", EnumStorageType.AUDIO)
        }
        finally {
            tmpFile.delete()
            tmpLog.useThenDelete { EternalJukebox.storage.store(it.name, EnumStorageType.LOG, FileDataSource(it)) }
            ffmpegLog.useThenDelete { EternalJukebox.storage.store(it.name, EnumStorageType.LOG, FileDataSource(it)) }
            endGoalTmp.useThenDelete { EternalJukebox.storage.store("${info.id}.$format", EnumStorageType.AUDIO, FileDataSource(it)) }
        }
    }

    fun getContentDetailsWithKey(id: String): YoutubeContentItem? {
        val (_, _, r) = Fuel.get("https://www.googleapis.com/youtube/v3/videos", listOf("part" to "contentDetails,snippet", "id" to id, "key" to (apiKey ?: return null)))
                .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
                .responseString()

        val (result, error) = r

        if(error != null)
            return null

        return EternalJukebox.jsonMapper.readValue(result, YoutubeContentResults::class.java).items.firstOrNull()
    }

    fun getMultiContentDetailsWithKey(ids: List<String>): List<YoutubeContentItem> {
        val (_, _, r) = Fuel.get("https://www.googleapis.com/youtube/v3/videos", listOf("part" to "contentDetails,snippet", "id" to ids.joinToString(), "key" to (apiKey ?: return emptyList())))
                .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
                .responseString()

        val (result, error) = r

        if(error != null)
            return emptyList()

        return EternalJukebox.jsonMapper.readValue(result, YoutubeContentResults::class.java).items
    }

    fun searchYoutubeWithKey(query: String, maxResults: Int = 5): List<YoutubeSearchItem> {
        val (_, _, r) = Fuel.get("https://www.googleapis.com/youtube/v3/search", listOf("part" to "snippet", "q" to query, "maxResults" to "$maxResults", "key" to (apiKey ?: return emptyList()), "type" to "video"))
                .header("User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.11; rv:44.0) Gecko/20100101 Firefox/44.0")
                .responseString()

        val (result, error) = r

        if(error != null)
            return ArrayList()

        return EternalJukebox.jsonMapper.readValue(result, YoutubeSearchResults::class.java).items
    }

    init {
        apiKey = EternalJukebox.config.audioSourceOptions["API_KEY"] as? String
        format = EternalJukebox.config.audioSourceOptions["AUDIO_FORMAT"] as? String ?: "m4a"
        command = (EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"] as? List<*>)?.map { "$it" } ?: (EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"] as? String)?.split("\\s+".toRegex()) ?: if(System.getProperty("os.name").toLowerCase().contains("windows")) listOf("yt.bat") else listOf("bash", "yt.sh")

        if(apiKey == null)
            log("Warning: No API key provided. We're going to scrape the Youtube search page which is a not great thing to do.\nTo obtain an API key, follow the guide here (https://developers.google.com/youtube/v3/getting-started) or over on the EternalJukebox Github page!", true)
    }
}