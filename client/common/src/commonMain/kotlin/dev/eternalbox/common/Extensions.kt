package dev.eternalbox.common

import kotlinx.coroutines.*
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

public fun IntArray.sumUntil(index: Int): Int {
    var sum: Int = 0
    for (i in 0 until index) {
        sum += this[i]
    }
    return sum
}

suspend fun <T> arbitraryProgressBar(
        delay: Long = 200, limit: Int = 9,
        start: Char = '[', end: Char = ']',
        space: Char = ' ', indicator: Char = 'o',
        loadingText: String? = "ascii.arbitrary.loading",
        loadedText: String? = "ascii.arbitrary.loaded!",
        operation: () -> T
): T = coroutineScope {
    val arbitrary = arbitraryProgressBar(delay, limit, start, end, space, indicator, loadingText, loadedText)
    try {
        return@coroutineScope operation()
    } finally {
        arbitrary.cancelAndJoin()
    }
}

suspend fun <T> arbitrarySuspendedProgressBar(
        delay: Long = 200, limit: Int = 9,
        start: Char = '[', end: Char = ']',
        space: Char = ' ', indicator: Char = 'o',
        loadingText: String = "Loading...",
        loadedText: String = "Loaded!",
        operation: suspend CoroutineScope.() -> T
): T = coroutineScope {
    val arbitrary = arbitraryProgressBar(delay, limit, start, end, space, indicator, loadingText, loadedText)
    try {
        return@coroutineScope operation()
    } finally {
        arbitrary.cancelAndJoin()
    }
}

@OptIn(ExperimentalTime::class)
fun CoroutineScope.arbitraryProgressBar(
        delay: Long = 200, limit: Int = 9,
        start: Char = '[', end: Char = ']',
        space: Char = ' ', indicator: Char = 'o',
        loadingText: String? = "Loading...",
        loadedText: String? = "Loaded!"
): Job = launch {
    val mark = TimeSource.Monotonic.markNow()

    try {
        var progress: Int = 0
        var goingRight: Boolean = true

        while (isActive) {
            print(buildString {
                append('\r')
                append(start)
                for (i in 0 until progress)
                    append(space)
                append(indicator)
                for (i in 0 until (limit - progress))
                    append(space)
                append(end)
                append(' ')
                loadingText?.let { append(it) }
            })

            if (goingRight)
                progress++
            else
                progress--

            if (progress == limit || progress == 0)
                goingRight = !goingRight

            delay(delay)
        }
    } finally {
        if (loadedText != null) {
            print(buildString {
                append('\r')
                for (i in 0 until limit)
                    append(' ')
                append("    ")
                for (i in 0 until (loadingText?.length ?: 0))
                    append(' ')
                append('\r')
            })

            println(loadedText.replace("{0}", mark.elapsedNow().toString()))
        }
    }
}