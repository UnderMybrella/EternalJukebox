package dev.eternalbox.common.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

suspend inline fun <T> Flow<T?>.firstNotNull(): T =
    first { it != null }!!