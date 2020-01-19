package dev.eternalbox.eternaljukebox.providers.audio

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface AudioProviderFactory<T: AudioProvider> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}