package dev.eternalbox.eternaljukebox.providers.info

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface InfoProviderFactory<T: InfoProvider> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}