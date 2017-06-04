package org.abimon.eternalJukebox

import org.abimon.eternalJukebox.objects.ErroredResponse

public infix fun <R, E> R?.withError(e: E?): ErroredResponse<R?, E?> = ErroredResponse(this, e)