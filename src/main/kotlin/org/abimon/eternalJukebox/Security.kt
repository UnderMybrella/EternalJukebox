package org.abimon.eternalJukebox

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor


private val HMAC_SHA1_ALGORITHM = "HmacSHA1"

private fun ByteArray.toHexString(): String {
    val formatter = Formatter()

    for (b in this) {
        formatter.format("%02x", b)
    }

    return formatter.toString()
}

fun calculateRFC2104HMAC(data: String, key: String): String {
    val signingKey = SecretKeySpec(key.toByteArray(), HMAC_SHA1_ALGORITHM)
    val mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
    mac.init(signingKey)
    return mac.doFinal(data.toByteArray()).toHexString()
}

fun String.slowEquals(other: String): Boolean {
    val a = this.toByteArray()
    val b = other.toByteArray()
    var diff = a.size xor b.size
    var i = 0
    while (i < a.size && i < b.size) {
        diff = diff or (a[i] xor b[i]).toInt()
        i++
    }
    return diff == 0
}