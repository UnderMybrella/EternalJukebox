package org.abimon.eternalJukebox.exceptions

class HttpStatusCodeException(val statusCode: Int): RuntimeException()