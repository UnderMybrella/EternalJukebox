package dev.eternalbox.eternaljukebox.providers.audio

import java.io.File

class YtdlProcessBuilder(val urls: Array<out String>) {
    companion object {
        val FFMPEG_ERROR = "ERROR: ffprobe/avprobe and ffmpeg/avconv not found. Please install one."
    }

    val options: MutableMap<String, String> = LinkedHashMap()

    fun help() = option("--help")
    fun version() = option("--version")
    fun update() = option("--update")
    fun ignoreErrors() = option("--ignore-errors")
    fun abortOnError() = option("--abort-on-error")
    fun dumpUserAgent() = option("--dump-user-agent")
    fun listExtractors() = option("--list-extractors")
    fun extractorDescriptions() = option("--extractor-descriptions")
    fun forceGenericExtractor() = option("--force-generic-extractor")
    fun defaultSearch(prefix: String) = option("--default-search", prefix)
    fun ignoreConfig() = option("--ignore-config")
    fun configLocation(path: String) = option("--config-location", path)
    fun flatPlaylist() = option("--flat-playlist")
    fun markWatched() = option("--mark-watched")
    fun noMarkWatched() = option("--no-mark-watched")
    fun noColor() = option("--no-color")

    /** Network Options */

    fun proxy(url: String) = option("--proxy", url)

    fun socketTimeout(seconds: Int) = option("--socket-timeout", seconds.toString())
    fun sourceAddress(ip: String) = option("--source-address", ip)
    fun forceIPv4() = option("--force-ipv4")
    fun forceIPv6() = option("--force-ipv6")

    /** Geo Restriction */

    fun geoVerificationProxy(url: String) = option("--geo-verification-url", url)

    fun geoBypass() = option("--geo-bypass")
    fun noGeoBypass() = option("--no-geo-bypass")
    fun geoBypassCountry(code: String) = option("--geo-bypass-country", code)
    fun geoBypassIpBlock(ipBlock: String) = option("--geo-bypass-ip-block", ipBlock)

    /** Video Selection */

    fun playlistStart(number: Int) = option("--playlist-start", number.toString())

    fun playlistEnd(number: Int) = option("--playlist-end", number.toString())
    fun playlistItems(vararg indices: Int) = playlistItems(indices.joinToString(","))
    fun playlistItems(spec: String) = option("--playlist-items", spec)
    fun matchTitle(regex: String) = option("--match-title", regex)
    fun rejectTitle(regex: String) = option("--reject-title", regex)
    fun maxDownloads(number: Int) = option("--max-downloads", number.toString())
    fun minFilesize(size: Int) = option("--min-filesize", size.toString())
    fun maxFilesize(size: Int) = option("--max-filesize", size.toString())
    fun date(date: String) = option("--date", date)
    fun dateBefore(date: String) = option("--datebefore", date)
    fun dateAfter(date: String) = option("--dateafter", date)
    fun minViews(count: Int) = option("--min-views", count.toString())
    fun maxViews(count: Int) = option("--max-views", count.toString())
    fun matchFilter(filter: String) = option("--match-filter", filter)
    fun noPlaylist() = option("--no-playlist")
    fun yesPlaylist() = option("--yes-playlist")
    fun ageLimit(age: Int) = option("--age-limit", age.toString())
    fun downloadArchive(file: String) = option("--download-archive", file)
    fun includeAds() = option("--include-ads")

    /** Download Options */
    fun limitRate(rate: Long) = option("--limit-rate", rate.toString())

    fun retries(count: Int) = option("--retries", if (count == -1) "infinite" else count.toString())
    fun fragmentRetries(count: Int) = option("--fragment-retries", if (count == -1) "infinite" else count.toString())
    fun skipUnavailableFragments() = option("--skip-unavailable-fragments")
    fun abortOnUnavailableFragment() = option("--abort-on-unavailable-fragment")
    fun keepFragments() = option("--keep-fragments")
    fun bufferSize(size: Long) = option("--buffer-size", size.toString())
    fun noResizeBuffer() = option("--no-resize-buffer")
    fun httpChunkSize(size: Long) = option("--http-chunk-size", size.toString())
    fun playlistReverse() = option("--playlist-reverse")
    fun playlistRandom() = option("--playlist-random")
    fun xattrSetFilesize() = option("--xattr-set-filesize")
    fun hlsPreferNative() = option("--hls-prefer-native")
    fun hlsPreferFFmpeg() = option("--hls-prefer-ffmpeg")
    fun hlsUseMpegts() = option("--hls-use-mpegts")
    fun externalDownloader(command: String) = option("--external-downloader", command)

    /** Filesystem Options */
    fun batchFile(file: File) = batchFile(file.absolutePath)

    fun batchFile(file: String) = option("--batch-file", file)
    fun id() = option("--id")
    fun output(template: String) = option("--output", template)
    fun autonumberStart(number: Int) = option("--autonumber-start", number.toString())
    fun restrictFilenames() = option("--restrict-filenames")
    fun noOverwrites() = option("--no-overwrites")
    fun continuePartialDownloads() = option("--continue")
    fun noContinue() = option("--no-continue")
    fun noPart() = option("--no-part")
    fun noMtime() = option("--no-mtime")
    fun writeDescription() = option("--write-description")
    fun writeInfoJson() = option("--write-info-json")
    fun writeAnnotations() = option("--write-annotations")
    fun loadInfoJson(file: File) = loadInfoJson(file.absolutePath)
    fun loadInfoJson(file: String) = option("--load-info-json", file)
    fun cookies(file: File) = cookies(file.absolutePath)
    fun cookies(file: String) = option("--cookies", file)
    fun cacheDir(file: File) = cacheDir(file.absolutePath)
    fun cacheDir(file: String) = option("--cache-dir", file)
    fun noCacheDir() = option("--no-cache-dir")
    fun rmCacheDir() = option("--rm-cache-dir")

    /** Thumbnail Images */
    fun writeThumbnail() = option("--write-thumbnail")

    fun writeAllThumbnails() = option("--write-all-thumbnails")
    fun listThumbnails() = option("--list-thumbnails")

    /** Verbosity / Simulation Options */
    fun quiet() = option("--quiet")

    fun noWarnings() = option("--no-warnings")
    fun simulate() = option("--simulate")
    fun skipDownload() = option("--skip-download")
    fun getUrl() = option("--get-url")
    fun getTitle() = option("--get-title")
    fun getId() = option("--get-id")
    fun getThumbnail() = option("--get-thumbnail")
    fun getDescription() = option("--get-description")
    fun getDuration() = option("--get-duration")
    fun getFilename() = option("--get-filename")
    fun getFormat() = option("--get-format")

    fun dumpJson() = option("--dump-json")
    fun dumpSingleJson() = option("--dump-single-json")
    fun printJson() = option("--print-json")
    fun newline() = option("--newline")
    fun noProgress() = option("--no-progress")
    fun consoleTitle() = option("--console-title")
    fun verbose() = option("--verbose")
    fun dumpPages() = option("--dump-pages")
    fun writePages() = option("--write-pages")
    fun printTraffic() = option("--print-traffic")
    fun callHome() = option("--call-home")
    fun noCallHome() = option("--no-call-home")

    /** Workarounds */

    fun encoding(encoding: String) = option("--encoding", encoding)

    fun noCheckCertificate() = option("--no-check-certificate")
    fun preferInsecure() = option("--prefer-insecure")
    fun userAgent(agent: String) = option("--user-agent", agent)
    fun referer(url: String) = option("--referer", url)
    fun addHeader(field: String, value: String) = option("--add-header", "$field:$value")
    fun bidiWorkaround() = option("--bidi-workaround")
    fun sleepInterval(seconds: Int) = option("--sleep-interval", seconds.toString())
    fun maxSleepInterval(seconds: Int) = option("--max-sleep-interval", seconds.toString())

    /** Video Format Options */
    fun format(format: String) = option("--format", format)

    fun allFormats() = option("--all-formats")
    fun preferFreeFormats() = option("--prefer-free-formats")
    fun listFormats() = option("--list-formats")
    fun youtubeSkipDashManifest() = option("--youtube-skip-dash-manifest")
    fun mergeOutputFormat(format: String) = option("--merge-output-format", format)

    /** Subtitle Options */
    fun writeSub() = option("--write-sub")

    fun writeAutoSub() = option("--write-auto-sub")
    fun allSubs() = option("--all-subs")
    fun listSubs() = option("--list-subs")
    fun subFormat(format: String) = option("--sub-format", format)
    fun subLang(vararg langs: String) = option("--sub-lang", langs.joinToString(","))

    /** Authentication Options */
    fun username(username: String) = option("--username", username)

    fun password(password: String) = option("--password", password)
    fun twofactor(code: String) = option("--twofactor", code)
    fun netrc() = option("--netrc")
    fun videoPassword(password: String) = option("--video-password", password)

    /** Adobe Pass Options */
    fun apMso() = option("--ap-mso")

    fun apUsername(username: String) = option("--ap-username", username)
    fun apPassword(password: String) = option("--ap-password", password)
    fun apListMso() = option("--ap-list-mso")

    /** Post-Processing Options */
    fun extractAudio() = option("--extract-audio")

    fun audioFormat(format: String) = option("--audio-format", format)
    fun audioQuality(quality: String) = option("--audio-quality", quality)
    fun recodeVideo(format: String) = option("--recode-video", format)
    fun postprocessorArgs(args: String) = option("--postprocessor-args", args)
    fun keepVideo() = option("--keep-video")
    fun noPostOverwrites() = option("--no-post-overwrites")
    fun embedSubs() = option("--embed-subs")
    fun embedThumbnail() = option("--embed-thumbnail")
    fun addMetadata() = option("--add-metadata")
    fun metadataFromTitle(format: String) = option("--metadata-from-title", format)
    fun xattrs() = option("--xattrs")
    fun fixup(policy: String) = option("--fixup", policy)
    fun preferAvconv() = option("--prefer-avconv")
    fun preferFFmpeg() = option("--prefer-ffmpeg")
    fun ffmpegLocation(location: File) = ffmpegLocation(location.absolutePath)
    fun ffmpegLocation(location: String) = option("--ffmpeg-location", location)
    fun exec(cmd: String) = option("--exec", cmd)
    fun convertSubs(format: String) = option("--convert-subs", format)

    fun option(flag: String, value: String = ""): YtdlProcessBuilder {
        options[flag] = value
        return this
    }

    fun build(): Process {
        val args: MutableList<String> = ArrayList()
        args.add("youtube-dl")
        options.forEach { (k, v) ->
            args.add(k)
            args.add(v)
        }
        args.addAll(urls)
        val builder = ProcessBuilder(args)
            .inheritIO()

//        builder.environment()["Path"] =
//            "C:\\Program Files (x86)\\Razer Chroma SDK\\bin;C:\\Program Files\\Razer Chroma SDK\\bin;C:\\Program Files (x86)\\Razer\\ChromaBroadcast\\bin;C:\\Program Files\\Razer\\ChromaBroadcast\\bin;C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\;C:\\Windows\\System32\\OpenSSH\\;C:\\Program Files\\Git\\cmd;C:\\Program Files\\dotnet\\;C:\\Program Files\\Microsoft SQL Server\\130\\Tools\\Binn\\;C:\\Program Files\\Microsoft SQL Server\\Client SDK\\ODBC\\170\\Tools\\Binn\\;C:\\Users\\under\\AppData\\Local\\Microsoft\\WindowsApps;C:\\Users\\under\\.dotnet\\tools;C:\\Program Files\\010 Editor;C:\\Program Files\\PuTTY\\;C:\\Program Files\\NVIDIA Corporation\\NVIDIA NvDLISR;C:\\Program Files (x86)\\NVIDIA Corporation\\PhysX\\Common;C:\\Ruby26-x64\\bin;C:\\Users\\under\\AppData\\Local\\Microsoft\\WindowsApps;C:\\Program Files\\Java\\jdk-12.0.2\\bin;C:\\Users\\under\\AppData\\Local\\Programs\\Microsoft VS Code\\bin;C:\\Users\\under\\AppData\\Local\\GitHubDesktop\\bin;C:\\Users\\under\\PATH;"

        return builder
            .start()
    }
}

fun buildYtdlProcess(vararg urls: String, init: YtdlProcessBuilder.() -> Unit): Process {
    val builder = YtdlProcessBuilder(urls)
    builder.init()
    return builder.build()
}