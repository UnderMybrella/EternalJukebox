package dev.eternalbox.server

import dev.eternalbox.analysis.spotify.SpotifyAnalysisApi
import dev.eternalbox.common.utils.TokenStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
suspend fun main(args: Array<String>) {
    val spotify = SpotifyAnalysisApi(args[0], args[1])

    listOf(
        "72XCIYkEtLwUYU911iGduu",
        "5WGatOLP3uqV4mh8jkloUv",
        "1QGzvMQyU4PQ2lJkvmqvVJ",
        "7AMA1BVMMitfR8i7fIUv5U",
        "0jqK7sGTLsHPkQrrcrGuKD"
    ).forEach { trackID ->
        println(spotify.getTrackDetails(trackID))
    }

    spotify.getAnalysis("0jqK7sGTLsHPkQrrcrGuKD")

//    val tokenStore = TokenStore(GlobalScope) { TokenStore.TokenResult(UUID.randomUUID(), Duration.Companion.seconds(30) )}
//
//    tokenStore.token.onEach { println(it) }.launchIn(GlobalScope)
//
//    while (true) {
//        delay(5_000)
//        println("--Cancelling")
//        tokenStore.refreshToken()
//        println("--Resuming")
//
//        delay(5_000)
//    }
}