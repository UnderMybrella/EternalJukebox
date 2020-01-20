package dev.eternalbox.eternaljukebox.routes

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface RouteFactory<T : EternalboxRoute> {
    val name: String

    fun build(jukebox: EternalJukebox): T
}