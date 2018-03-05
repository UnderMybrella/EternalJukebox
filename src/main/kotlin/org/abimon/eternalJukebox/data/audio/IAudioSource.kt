package org.abimon.eternalJukebox.data.audio

import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.DataSource
import java.net.URL

@FunctionalInterface
interface IAudioSource {
    val audioSourceOptions
        get() = EternalJukebox.config.audioSourceOptions
    /**
     * Provide the audio data for a required song
     * Returns a data source pointing to a **valid audio file**, or null if none can be obtained
     */
    fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource?

    /**
     * Provide a location for a required song
     * The provided location may not be a direct download link, and may not contain valid audio data.
     * The provided location, however, should be a link to said song where possible, or return null if nothing could be found.
     */
    fun provideLocation(info: JukeboxInfo, clientInfo: ClientInfo?): URL? = null
}