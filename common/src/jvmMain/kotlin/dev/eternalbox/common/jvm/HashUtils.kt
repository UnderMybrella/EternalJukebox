package dev.eternalbox.common.jvm

import java.math.BigInteger
import java.security.MessageDigest

inline fun String.hash(algorithm: String): String =
    encodeToByteArray().hash(algorithm)

fun ByteArray.hash(algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    val hashBytes = md.digest(this)
    return String.format("%0${hashBytes.size shl 1}x", BigInteger(1, hashBytes))
}