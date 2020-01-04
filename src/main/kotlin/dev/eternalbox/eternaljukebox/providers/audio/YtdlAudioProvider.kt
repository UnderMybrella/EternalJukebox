package dev.eternalbox.eternaljukebox.providers.audio

import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.providers.info.getTrackInfo
import dev.eternalbox.eternaljukebox.retrieve
import dev.eternalbox.ytmusicapi.YoutubeMusicSearchResponse
import dev.eternalbox.ytmusicapi.getSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class YtdlAudioProvider(val jukebox: EternalJukebox) : AudioProvider {
    val ytdlCacheDir = File("ytdl-cache")

    override suspend fun supportsAudio(audioService: EnumAudioService, analysisService: EnumAnalysisService): Boolean =
        audioService == EnumAudioService.JUKEBOX

    override suspend fun retrieveAudioFor(
        audioService: EnumAudioService,
        analysisService: EnumAnalysisService,
        id: String
    ): JukeboxResult<DataResponse> {
        if (audioService != EnumAudioService.JUKEBOX) return JukeboxResult.KnownFailure(
            WebApiResponseCodes.INVALID_AUDIO_SERVICE,
            WebApiResponseMessages.INVALID_AUDIO_SERVICE,
            audioService
        )

        return jukebox.infoProvider.getTrackInfo(jukebox, analysisService, id)
            .mapAwait { response -> response.retrieve<EternalboxTrackInfo>(jukebox) }
            .flatMapAwait { track ->
                jukebox.youtubeMusicApi.search("${track.title} by ${track.artists.joinToString()}")
                    .map(YoutubeMusicSearchResponse::getSongs)
                    .map { songs -> songs.firstOrNull { song -> song.album?.albumName == track.album } ?: songs[0] }
                    .flatMapAwait { song ->
                        val uuid = UUID.randomUUID().toString()
                        val process = buildYtdlProcess("https://youtu.be/${song.songID}") {
                            abortOnError()
                            noProgress()
                            output("${ytdlCacheDir.absolutePath}${File.separator}$uuid.%(ext)s")
                            format("bestaudio/best")
                            extractAudio()
//                            if (track.album != null) matchFilter("album = '${track.album}'")
                            noPlaylist()
                            maxDownloads(1)
                        }

                        withContext(Dispatchers.IO) { process.waitFor(120, TimeUnit.SECONDS) }
                        if (process.exitValue() == 0) {
                            val output =
                                ytdlCacheDir.listFiles { file -> file.nameWithoutExtension == uuid }?.firstOrNull()
                            if (output == null) {
                                JukeboxResult.UnknownFailure()
                            } else {
                                jukebox.audioDataStore.storeAudio(
                                    audioService,
                                    analysisService,
                                    id,
                                    withContext(Dispatchers.IO) { output.readBytes() }
                                ).also { output.delete() }
                            }
                        } else {
                            JukeboxResult.KnownFailure(process.exitValue(), "")
                        }
                    }
            }
    }
}