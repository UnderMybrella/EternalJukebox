package org.abimon.eternalJukebox.objects

class ErroredResponse<out R, out E> (val response: R?, val error: E) {
    operator fun component1(): R? = response
    operator fun component2(): E? = error
}