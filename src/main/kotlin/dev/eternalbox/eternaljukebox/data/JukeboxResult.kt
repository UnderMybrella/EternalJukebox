package dev.eternalbox.eternaljukebox.data

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class JukeboxResult<T> {
    data class Success<T>(val result: T) : JukeboxResult<T>() {
        override fun <R> map(mapper: (T) -> R): Success<R> = Success(mapper(result))
        override suspend fun <R> mapAwait(mapper: suspend (T) -> R): JukeboxResult<R> = Success(mapper(result))

        override fun <R> flatMap(mapper: (T) -> JukeboxResult<R>): JukeboxResult<R> = mapper(result)
        override suspend fun <R> flatMapAwait(mapper: suspend (T) -> JukeboxResult<R>) = mapper(result)

        override fun filter(filter: (T) -> Boolean): JukeboxResult<T> {
            if (filter(result))
                return this
            return UnknownFailure()
        }

        override suspend fun filterAwait(filter: suspend (T) -> Boolean): JukeboxResult<T> {
            if (filter(result))
                return this
            return UnknownFailure()
        }

        override fun run(block: (T) -> Unit): JukeboxResult<T> {
            block(result)
            return this
        }

        override suspend fun runAwait(block: suspend (T) -> Unit): JukeboxResult<T> {
            block(result)
            return this
        }
    }

    abstract class Failure<T> : JukeboxResult<T>() {
        override fun filter(filter: (T) -> Boolean): JukeboxResult<T> = this
        override suspend fun filterAwait(filter: suspend (T) -> Boolean): JukeboxResult<T> = this

        override fun run(block: (T) -> Unit): JukeboxResult<T> = this
        override suspend fun runAwait(block: suspend (T) -> Unit): JukeboxResult<T> = this
    }

    data class KnownFailure<T, U>(val errorCode: Int, val errorMessage: String, val additionalInfo: U?) : Failure<T>() {
        companion object {
            operator fun <T> invoke(errorCode: Int, errorMessage: String) =
                KnownFailure<T, Any>(errorCode, errorMessage, null)
        }

        override fun <R> flatMap(mapper: (T) -> JukeboxResult<R>): JukeboxResult<R> =
            KnownFailure(errorCode, errorMessage, additionalInfo)

        override suspend fun <R> flatMapAwait(mapper: suspend (T) -> JukeboxResult<R>): JukeboxResult<R> =
            KnownFailure(errorCode, errorMessage, additionalInfo)

        override fun <R> map(mapper: (T) -> R): JukeboxResult<R> = KnownFailure(errorCode, errorMessage, additionalInfo)
        override suspend fun <R> mapAwait(mapper: suspend (T) -> R): JukeboxResult<R> =
            KnownFailure(errorCode, errorMessage, additionalInfo)
    }

    class UnknownFailure<T> : Failure<T>() {
        override fun <R> flatMap(mapper: (T) -> JukeboxResult<R>): JukeboxResult<R> = UnknownFailure()
        override suspend fun <R> flatMapAwait(mapper: suspend (T) -> JukeboxResult<R>): JukeboxResult<R> =
            UnknownFailure()

        override fun <R> map(mapper: (T) -> R): JukeboxResult<R> = UnknownFailure()
        override suspend fun <R> mapAwait(mapper: suspend (T) -> R): JukeboxResult<R> = UnknownFailure()
    }

    val didSucceed: Boolean
        get() = this is Success
    val didFail: Boolean
        get() = this is Failure

    abstract fun <R> map(mapper: (T) -> R): JukeboxResult<R>
    abstract suspend fun <R> mapAwait(mapper: suspend (T) -> R): JukeboxResult<R>

    abstract fun <R> flatMap(mapper: (T) -> JukeboxResult<R>): JukeboxResult<R>
    abstract suspend fun <R> flatMapAwait(mapper: suspend (T) -> JukeboxResult<R>): JukeboxResult<R>

    abstract fun filter(filter: (T) -> Boolean): JukeboxResult<T>
    abstract suspend fun filterAwait(filter: suspend (T) -> Boolean): JukeboxResult<T>

    abstract fun run(block: (T) -> Unit): JukeboxResult<T>
    abstract suspend fun runAwait(block: suspend (T) -> Unit): JukeboxResult<T>
}

@ExperimentalContracts
fun <T> JukeboxResult<T>.isUnauthenticatedFailure(): Boolean {
    contract {
        returns(true) implies (this@isUnauthenticatedFailure is JukeboxResult.KnownFailure<*, *>)
    }

    return this is JukeboxResult.KnownFailure<*, *> && this.errorCode == 401
}

@ExperimentalContracts
fun <T> JukeboxResult<T>.isGatewayTimeout(): Boolean {
    contract {
        returns(true) implies (this@isGatewayTimeout is JukeboxResult.KnownFailure<*, *>)
    }

    return this is JukeboxResult.KnownFailure<*, *> && this.errorCode == 504
}