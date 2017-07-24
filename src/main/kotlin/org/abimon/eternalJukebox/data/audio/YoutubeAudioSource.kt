package org.abimon.eternalJukebox.data.audio

import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.DataSource

object YoutubeAudioSource: IAudioSource {
    override fun provide(info: JukeboxInfo): DataSource? {
        return null
    }
}