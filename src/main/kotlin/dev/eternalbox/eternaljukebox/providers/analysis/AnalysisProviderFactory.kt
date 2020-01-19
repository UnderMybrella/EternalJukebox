package dev.eternalbox.eternaljukebox.providers.analysis

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface AnalysisProviderFactory<T: AnalysisProvider> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}