package dev.eternalbox.common.utils

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TokenStore<T>(scope: CoroutineScope, context: CoroutineContext = EmptyCoroutineContext, val processing: suspend () -> TokenResult<T>?) {
    data class TokenResult<T>(val data: T, val validFor: Duration)

    private val _token: MutableStateFlow<T?> = MutableStateFlow(null)
    val token = _token.asStateFlow()

    var delayJob: Job? = null
    val waitingForTokenRefresh: MutableList<CancellableContinuation<Unit>> = ArrayList()

    var debugProcessingError: ((Throwable) -> Unit)? = null
    var debugReload: ((String) -> Unit)? = null
    var debugSleeping: ((String, Duration) -> Unit)? = null

    val tokenJob = scope.launch(context) {
        while (isActive) {
            debugReload?.invoke("Reloading token")
            _token.emit(null)

            var result: TokenResult<T>? = null
            for (i in 0 until 8) {
                try {
                    result = processing()
                    if (result != null) break
                } catch (th: Throwable) {
                    debugProcessingError?.invoke(th)
                }

                delay(((2.0.pow(i) + Random.nextDouble()) * 1000L).roundToLong())
            }

            if (result == null) continue
            _token.emit(result.data)

            debugSleeping?.invoke("Sleeping for {}", result.validFor)
            delayJob?.cancel()

            while (waitingForTokenRefresh.isNotEmpty()) waitingForTokenRefresh.removeLast().resume(Unit)

            delayJob = launch { delay(result.validFor - Duration.minutes(1)) }
            delayJob?.join()
        }
    }

    suspend fun refreshToken(previous: T? = null) {
        if (_token.compareAndSet(previous, null) || _token.compareAndSet(null, null)) {
            suspendCancellableCoroutine<Unit> { continuation ->
                waitingForTokenRefresh.add(continuation)
                delayJob?.cancel()
            }
        }
    }
}