package dev.eternalbox.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A counting semaphore for coroutines that logically maintains a number of available permits.
 * Each [acquire] takes a single permit or suspends until it is available.
 * Each [release] adds a permit, potentially releasing a suspended acquirer.
 * Semaphore is fair and maintains a FIFO order of acquirers.
 *
 * Semaphores are mostly used to limit the number of coroutines that have an access to particular resource.
 * Semaphore with `permits = 1` is essentially a [Mutex].
 *
 *
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
//TODO: Look at a PR to kotlinx coroutines instead, which would give us better internal access
public interface ReadWriteSemaphore : Semaphore {
    /**
     * Returns the current number of read permits available in this semaphore.
     */
    public val availableReadPermits: Int
    public val writePermitAvailable: Boolean

    override val availablePermits: Int get() = availableReadPermits

    /**
     * Acquires a read permit from this semaphore, suspending until one is available.
     * All suspending acquirers are processed in first-in-first-out (FIFO) order.
     *
     * This suspending function is cancellable. If the [Job] of the current coroutine is cancelled or completed while this
     * function is suspended, this function immediately resumes with [CancellationException].
     *
     * *Cancellation of suspended semaphore acquisition is atomic* -- when this function
     * throws [CancellationException] it means that the semaphore was not acquired.
     *
     * Note, that this function does not check for cancellation when it does not suspend.
     * Use [CoroutineScope.isActive] or [CoroutineScope.ensureActive] to periodically
     * check for cancellation in tight loops if needed.
     *
     * Use [tryAcquireReadPermit] to try acquire a permit of this semaphore without suspension.
     */
    public suspend fun acquireReadPermit()
    override suspend fun acquire(): Unit = acquireReadPermit()


    /**
     * Acquires a write permit from this semaphore, suspending until one is available, and all read permits have been released.
     * All suspending acquirers are processed in first-in-first-out (FIFO) order.
     *
     * This suspending function is cancellable. If the [Job] of the current coroutine is cancelled or completed while this
     * function is suspended, this function immediately resumes with [CancellationException].
     *
     * *Cancellation of suspended semaphore acquisition is atomic* -- when this function
     * throws [CancellationException] it means that the semaphore was not acquired.
     *
     * Note, that this function does not check for cancellation when it does not suspend.
     * Use [CoroutineScope.isActive] or [CoroutineScope.ensureActive] to periodically
     * check for cancellation in tight loops if needed.
     *
     * Use [tryAcquireWritePermit] to try acquire a permit of this semaphore without suspension.
     */
    public suspend fun acquireWritePermit()

    /**
     * Tries to acquire a read permit from this semaphore without suspension.
     *
     * @return `true` if a permit was acquired, `false` otherwise.
     */
    public fun tryAcquireReadPermit(): Boolean
    override fun tryAcquire(): Boolean = tryAcquireReadPermit()

    /**
     * Tries to acquire a write permit from this semaphore without suspension.
     *
     * @return `true` if a permit was acquired, `false` otherwise.
     */
    public fun tryAcquireWritePermit(): Boolean

    /**
     * Releases a read permit, returning it into this semaphore. Resumes the first
     * suspending acquirer if there is one at the point of invocation.
     * Throws [IllegalStateException] if the number of [releaseReadPermit] invocations is greater than the number of preceding [acquireReadPermit].
     */
    public fun releaseReadPermit()
    override fun release(): Unit = releaseReadPermit()

    /**
     * Releases a read permit, returning it into this semaphore. Resumes the first
     * suspending acquirer if there is one at the point of invocation.
     * Throws [IllegalStateException] if the number of [releaseReadPermit] invocations is greater than the number of preceding [acquireReadPermit].
     */
    public fun releaseWritePermit()
}

/**
 * Creates new [Semaphore] instance.
 * @param permits the number of permits available in this semaphore.
 * @param acquiredPermits the number of already acquired permits,
 *        should be between `0` and `permits` (inclusively).
 */
@Suppress("FunctionName")
public fun ReadWriteSemaphore(permits: Int, acquiredPermits: Int = 0): ReadWriteSemaphore =
    ReadWriteSemaphoreImpl(permits, acquiredPermits)

/**
 * Executes the given [action], acquiring a read permit from this semaphore at the beginning
 * and releasing it after the [action] is completed.
 *
 * @return the return value of the [action].
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun <T> ReadWriteSemaphore.withReadPermit(action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    acquireReadPermit()
    try {
        return action()
    } finally {
        releaseReadPermit()
    }
}


/**
 * Executes the given [action], acquiring a read permit from this semaphore at the beginning
 * and releasing it after the [action] is completed.
 *
 * @return the return value of the [action].
 */
@OptIn(ExperimentalContracts::class)
public suspend inline fun <T> ReadWriteSemaphore.withWritePermit(action: () -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }

    acquireWritePermit()
    try {
        return action()
    } finally {
        releaseWritePermit()
    }
}

public suspend inline fun Semaphore.waitFor() {
    acquire()
    release()
}

private class ReadWriteSemaphoreImpl(private val permits: Int, acquiredPermits: Int) : ReadWriteSemaphore {
    private val readingSemaphore: Semaphore = Semaphore(permits, acquiredPermits)
    private val writingSemaphore: Semaphore = Semaphore(1, 0)

//    private val _writing = atomic(0)

    override val availableReadPermits: Int by readingSemaphore::availablePermits
    override val writePermitAvailable: Boolean get() = writingSemaphore.availablePermits > 0

    override fun tryAcquireReadPermit(): Boolean {
        if (!writePermitAvailable) return false

        return readingSemaphore.tryAcquire()
    }

    override fun tryAcquireWritePermit(): Boolean {
        if (!writingSemaphore.tryAcquire()) return false
        repeat(permits) { c ->
            if (!readingSemaphore.tryAcquire()) {
                repeat(c) { readingSemaphore.release() }

                return false
            }
        }

        return true
    }

    override suspend fun acquireReadPermit() {
//        println("Acquiring read permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
        if (!writePermitAvailable) writingSemaphore.waitFor()

        readingSemaphore.acquire()
//        println("Acquired read permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
    }

    override suspend fun acquireWritePermit() {
//        println("Acquiring write permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
//        _writing.value++

        writingSemaphore.acquire()
        repeat(permits) { readingSemaphore.acquire() }

//        println("Acquired write permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
    }

    override fun releaseReadPermit() {
//        println("Releasing read permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
        readingSemaphore.release()
//        println("Released read permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
    }
    override fun releaseWritePermit() {
//        println("Releasing write permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
//        _writing.value--

        writingSemaphore.release()
        repeat(permits) { readingSemaphore.release() }

//        println("Released write permit: (${readingSemaphore.availablePermits}/$permits, ${_writing.value})")
    }
}