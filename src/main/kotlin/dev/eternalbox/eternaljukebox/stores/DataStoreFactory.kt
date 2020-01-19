package dev.eternalbox.eternaljukebox.stores

import dev.eternalbox.eternaljukebox.EternalJukebox
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface AnalysisDataStoreFactory<T : AnalysisDataStore> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface AudioDataStoreFactory<T : AudioDataStore> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}

@ExperimentalCoroutinesApi
@ExperimentalContracts
interface InfoDataStoreFactory<T : InfoDataStore> {
    val name: String

    fun configure(jukebox: EternalJukebox)
    fun build(jukebox: EternalJukebox): T
}


@ExperimentalCoroutinesApi
@ExperimentalContracts
interface DataStoreFactory<T> : AnalysisDataStoreFactory<T>, AudioDataStoreFactory<T>, InfoDataStoreFactory<T>
        where T : AnalysisDataStore, T : AudioDataStore, T : InfoDataStore
