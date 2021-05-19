//package dev.eternalbox.client.jvm.magma
//
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.*
//import kotlin.coroutines.ContinuationInterceptor
//import kotlin.coroutines.CoroutineContext
//import kotlin.coroutines.EmptyCoroutineContext
//import kotlin.experimental.ExperimentalTypeInference
//
///**
// * Launches a new coroutine to produce a stream of values by sending them to a channel
// * and returns a reference to the coroutine as a [ReceiveChannel]. This resulting
// * object can be used to [receive][ReceiveChannel.receive] elements produced by this coroutine.
// *
// * The scope of the coroutine contains the [ProducerScope] interface, which implements
// * both [CoroutineScope] and [SendChannel], so that the coroutine can invoke
// * [send][SendChannel.send] directly. The channel is [closed][SendChannel.close]
// * when the coroutine completes.
// * The running coroutine is cancelled when its receive channel is [cancelled][ReceiveChannel.cancel].
// *
// * The coroutine context is inherited from this [CoroutineScope]. Additional context elements can be specified with the [context] argument.
// * If the context does not have any dispatcher or other [ContinuationInterceptor], then [Dispatchers.Default] is used.
// * The parent job is inherited from the [CoroutineScope] as well, but it can also be overridden
// * with a corresponding [context] element.
// *
// * Any uncaught exception in this coroutine will close the channel with this exception as the cause and
// * the resulting channel will become _failed_, so that any attempt to receive from it thereafter will throw an exception.
// *
// * The kind of the resulting channel depends on the specified [capacity] parameter.
// * See the [Channel] interface documentation for details.
// *
// * See [newCoroutineContext] for a description of debugging facilities available for newly created coroutines.
// *
// * **Note: This is an experimental api.** Behaviour of producers that work as children in a parent scope with respect
// *        to cancellation and error handling may change in the future.
// *
// * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
// * @param capacity capacity of the channel's buffer (no buffer by default).
// * @param block the coroutine code.
// */
//@OptIn(ExperimentalTypeInference::class)
//@ExperimentalCoroutinesApi
//public fun <E> CoroutineScope.exchange(
//        context: CoroutineContext = EmptyCoroutineContext,
//        capacity: Int = 0,
//        @BuilderInference block: suspend ExchangerScope<E>.() -> Unit
//): ExchangeChannel<E> {
//    val outputChannel = Channel<E>(capacity)
//    val inputChannel = Channel<E>(capacity)
//    val newContext = newCoroutineContext(context)
//    val coroutine = ExchangerCoroutine(newContext, outputChannel, inputChannel)
//    coroutine.start(CoroutineStart.DEFAULT, coroutine._exchanger, block)
//    return coroutine
//}
//
///**
// * Scope for the [exchange][CoroutineScope.exchange] coroutine builder.
// *
// * **Note: This is an experimental api.** Behavior of exchangers that work as children in a parent scope with respect
// *        to cancellation and error handling may change in the future.
// */
//@ExperimentalCoroutinesApi
//public interface ExchangerScope<E> : CoroutineScope, Channel<E> {
//    /** Channel this scope **receives** from. */
//    val inputAdvanced: Channel<E>
//
//    /** Channel this scope **sends** to. */
//    val outputAdvanced: Channel<E>
//
//    val input: ReceiveChannel<E>
//        get() = inputAdvanced
//    val output: SendChannel<E>
//        get() = outputAdvanced
//}
//
//@OptIn(InternalCoroutinesApi::class)
//@ExperimentalCoroutinesApi
//suspend inline fun <E> ExchangeChannel<E>.cycle(block: (E) -> E) {
//    while (!isClosedForReceive) send(block(receiveOrClosed().valueOrNull ?: break))
//}
//
//@OptIn(InternalCoroutinesApi::class)
//@ExperimentalCoroutinesApi
//suspend inline fun <E> ExchangeChannel<E>.flatCycle(block: (E) -> Unit) {
//    while (!isClosedForReceive) {
//        val e = receiveOrClosed().valueOrNull ?: break
//        block(e)
//        if (!isClosedForSend) {
//            send(e)
//        } else {
//            println("Couldn't send $e since we're closed for send; are we closed for receive? $isClosedForReceive")
//        }
//
//        yield() //Check for cancellation
//    }
//}
//
//@OptIn(InternalCoroutinesApi::class)
//@ExperimentalCoroutinesApi
//suspend inline fun <E, T> ExchangeChannel<E>.foldedCycle(initial: T, block: (T, E) -> T): T {
//    var acc = initial
//    while (!isClosedForReceive) {
//        val e = receiveOrClosed().valueOrNull ?: break
//        acc = block(acc, e)
//        send(e)
//    }
//
//    return acc
//}
//
//interface ExchangeChannel<E>: Channel<E>
//
//@ExperimentalCoroutinesApi
//data class ExchangerChannels<E>(val _input: Channel<E>, val _output: Channel<E>, val _scope: CoroutineScope): ExchangerScope<E>, Channel<E>, SendChannel<E> by _output, ReceiveChannel<E> by _input, CoroutineScope by _scope {
//    override val inputAdvanced: Channel<E> get() = _input
//    override val outputAdvanced: Channel<E> get() = _output
//
//    override fun close(cause: Throwable?): Boolean {
//        val a = _input.close(cause)
//        val b = _output.close(cause)
//
//        return a || b
//    }
//}
//
//@OptIn(InternalCoroutinesApi::class)
//@ExperimentalCoroutinesApi
//internal class ExchangerCoroutine<E>(parentContext: CoroutineContext, private val _outputChannel: Channel<E>, private val _inputChannel: Channel<E>) : AbstractCoroutine<Unit>(parentContext, true), ExchangeChannel<E>, ReceiveChannel<E> by _inputChannel, SendChannel<E> by _outputChannel {
//    internal val _exchanger = ExchangerChannels(_outputChannel, _inputChannel, this)
//
//    override fun cancel() {
//        cancel(null)
//    }
//
//    override fun cancel(cause: Throwable?): Boolean {
//        cancel(cause?.toCancellationException())
//
//        return true
//    }
//
//    override fun cancel(cause: CancellationException?) {
//        _inputChannel.cancel(cause) // cancel the channel
//        _outputChannel.cancel(cause)
//        cancelCoroutine(cause) // cancel the job
//    }
//}