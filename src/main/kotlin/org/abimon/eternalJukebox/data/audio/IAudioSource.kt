package org.abimon.eternalJukebox.data.audio

import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.DataSource

@FunctionalInterface
interface IAudioSource {
    /**
     * Provide the audio data for a required song
     * Returns a data source pointing to a **valid audio file**, or null if none can be obtained
     */
    fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource?
}