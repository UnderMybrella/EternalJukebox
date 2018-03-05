package org.abimon.eternalJukebox.data.audio

import org.abimon.eternalJukebox.data.NodeSource
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.visi.io.DataSource

@Suppress("UNCHECKED_CAST")
object NodeAudioSource: NodeSource(), IAudioSource {
    override val nodeHosts: Array<String>

    override fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? = provide("audio/${info.id}?user_uid=${clientInfo?.userUID}")

    init {
        if (audioSourceOptions.containsKey("NODE_HOST"))
            nodeHosts = arrayOf(audioSourceOptions["NODE_HOST"] as? String ?: throw IllegalArgumentException("${audioSourceOptions["NODE_HOST"]}  is not of type 'String' (is ${audioSourceOptions["NODE_HOST"]?.javaClass}"))
        else if (audioSourceOptions.containsKey("NODE_HOSTS")) {
            nodeHosts = (audioSourceOptions["NODE_HOSTS"] as? List<String>)?.toTypedArray() ?: throw throw IllegalArgumentException("${audioSourceOptions["NODE_HOSTS"]}  is not of type 'List<String>' (is ${audioSourceOptions["NODE_HOSTS"]?.javaClass}")
        } else
            throw IllegalArgumentException("No hosts assigned for NodeAudioSource")
    }
}