package dev.eternalbox.client.jvm.magma

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.brella.kornea.errors.common.KorneaResult
import dev.brella.kornea.errors.common.korneaNotFound
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SuspendedAudioLoadResultHandler(val continuation: Continuation<KorneaResult<out AudioItem>>): AudioLoadResultHandler {
    /**
     * Called when loading an item failed with an exception.
     * @param exception The exception that was thrown
     */
    override fun loadFailed(exception: FriendlyException) {
        continuation.resume(KorneaResult.thrown(exception))
    }

    /**
     * Called when the requested item is a track and it was successfully loaded.
     * @param track The loaded track
     */
    override fun trackLoaded(track: AudioTrack) {
        continuation.resume(KorneaResult.success(track, null))
    }

    /**
     * Called when there were no items found by the specified identifier.
     */
    override fun noMatches() {
        continuation.resume(korneaNotFound())
    }

    /**
     * Called when the requested item is a playlist and it was successfully loaded.
     * @param playlist The loaded playlist
     */
    override fun playlistLoaded(playlist: AudioPlaylist) {
        continuation.resume(KorneaResult.success(playlist, null))
    }
}

suspend fun AudioPlayerManager.loadItemAsync(identifier: String): KorneaResult<out AudioItem> =
        suspendCoroutine { cont -> loadItem(identifier, SuspendedAudioLoadResultHandler(cont)) }