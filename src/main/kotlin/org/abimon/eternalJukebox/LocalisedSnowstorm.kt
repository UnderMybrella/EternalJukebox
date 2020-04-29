package org.abimon.eternalJukebox

import kotlinx.coroutines.delay
import java.math.BigInteger
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.random.Random

/**
 * Snowflake

 * @author Maxim Khodanovich
 * *
 * @version 21.01.13 17:16
 * *
 *
 *
 * *          id is composed of:
 * *          time - 41 bits (millisecond precision w/ a custom epoch gives us 69 years)
 * *          configured machine id - 10 bits - gives us up to 1024 machines
 * *          sequence number - 12 bits - rolls over every 4096 per machine (with protection to avoid rollover in the same ms)
 */
class LocalisedSnowstorm constructor(private val twepoch: Long = LocalisedSnowstorm.defaultEpoch, datacenterOverride: Long? = null) {

    //   id format  =>
    //   timestamp |datacenter | sequence
    //   41        |10         |  12
    private val sequenceBits = 12
    private val datacenterIdBits = 10
    private val maxDatacenterId = (-1L shl datacenterIdBits).inv()

    private val datacenterIdShift = sequenceBits
    private val timestampLeftShift = sequenceBits + datacenterIdBits
    private val datacenterId: Long
    private val sequenceMax = 4096

    @Volatile private var lastTimestamp = -1L
    @Volatile private var sequence = 0

    init {
        datacenterId = datacenterOverride ?: getDatacenterId()
    }

    @Synchronized
    suspend fun generateLongId(): Long {
        var timestamp = System.currentTimeMillis()

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) % sequenceMax
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0
        }
        lastTimestamp = timestamp
        val id = timestamp - twepoch shl timestampLeftShift or
                (datacenterId shl datacenterIdShift) or
                sequence.toLong()
        return id
    }

    private suspend fun tilNextMillis(lastTimestamp: Long): Long {
        var timestamp = System.currentTimeMillis()
        while (timestamp <= lastTimestamp) {
            delay(lastTimestamp - timestamp - 1)
            timestamp = System.currentTimeMillis()
        }
        return timestamp
    }

    private fun getDatacenterId(): Long {
        try {
//            if (network == null) {
//                id = 1
//            } else {
//                val mac = network.hardwareAddress
//
//                if (mac == null) {
//                    id = 2
//                } else {
//                    id = 0x000000FFL and mac[mac.size - 1].toLong() or (0x0000FF00L and (mac[mac.size - 2].toLong() shl 8)) shr 6
//                }
//            }
            val addr = NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .flatMap { network -> network.inetAddresses.asSequence() }
                .firstOrNull(InetAddress::isSiteLocalAddress)

            val id: Int
            when (addr) {
                null -> id = Random.nextInt(0, 1023)
                else -> {
                    val addrData = addr.address

                    id = (addrData[addrData.size - 2].toInt() and 0x3 shl 8) or (addrData[addrData.size - 1].toInt() and 0xFF)
                }
            }

            println("Snowstorm Datacenter ID: $id")

            return id.toLong()
        } catch (e: SocketException) {
            e.printStackTrace()
            return 0
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            return 0
        }

    }

    suspend fun get(): Long {
        return generateLongId()
    }

    fun getSnowflakeTime(snowflake: String): LocalDateTime {
        val milliseconds = BigInteger.valueOf(twepoch).add(BigInteger(snowflake).shiftRight(22)).toLong()
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(milliseconds), ZoneId.systemDefault())
    }

    companion object WeatherMap {
        private val defaultEpoch = 1288834974657L

        private val snowstorms = HashMap<Long, LocalisedSnowstorm>()

        val instance: LocalisedSnowstorm
            get() = getInstance(defaultEpoch)

        fun getInstance(epoch: Long): LocalisedSnowstorm {
            if (!snowstorms.containsKey(epoch))
                snowstorms[epoch] = LocalisedSnowstorm(epoch)
            return snowstorms.getValue(epoch)
        }
    }
}