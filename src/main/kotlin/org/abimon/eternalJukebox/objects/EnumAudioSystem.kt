package org.abimon.eternalJukebox.objects

import org.abimon.eternalJukebox.data.audio.IAudioSource
import org.abimon.eternalJukebox.data.audio.NodeAudioSource
import org.abimon.eternalJukebox.data.audio.YoutubeAudioSource
import kotlin.reflect.KClass

enum class EnumAudioSystem(val audio: KClass<out IAudioSource>) {
    YOUTUBE(YoutubeAudioSource::class),
    NODE(NodeAudioSource::class)
}