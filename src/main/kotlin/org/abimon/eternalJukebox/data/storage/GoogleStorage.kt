package org.abimon.eternalJukebox.data.storage

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.sendBufferAwait
import io.vertx.kotlin.ext.web.client.sendFormAwait
import io.vertx.kotlin.ext.web.client.sendJsonAwait
import kotlinx.coroutines.*
import org.abimon.eternalJukebox.BufferDataSource
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.exponentiallyBackoff
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.redirect
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.HTTPDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

object GoogleStorage : IStorage {
    val serviceEmail: String
    val algorithm: Algorithm

    val accessTokenLock = ReentrantReadWriteLock()
    var googleAccessToken: String? = null
    val tokenContext = newSingleThreadContext("Google Storage Token Lock")

    val webClient = WebClient.create(EternalJukebox.vertx)
    val logger = LoggerFactory.getLogger("GoogleStorage")

    val storageBuckets: Map<EnumStorageType, String>
    val storageFolderPaths: Map<EnumStorageType, String>
    val storageSupportsCors: MutableList<EnumStorageType>

    val publicStorageTypes = arrayOf(
        EnumStorageType.UPLOADED_AUDIO,
        EnumStorageType.ANALYSIS,
        EnumStorageType.AUDIO,
        EnumStorageType.EXTERNAL_ANALYSIS,
        EnumStorageType.EXTERNAL_AUDIO
    )

    override fun shouldStore(type: EnumStorageType): Boolean =
        accessTokenLock.read { googleAccessToken != null && !disabledStorageTypes.contains(type) && storageBuckets[type] != null }

    override suspend fun store(
        name: String,
        type: EnumStorageType,
        data: DataSource,
        mimeType: String,
        clientInfo: ClientInfo?
    ): Boolean {
        if (!shouldStore(type))
            throw IllegalStateException("[${clientInfo?.userUID}] ERROR: Attempting to store $type, even though we've said not to store it! This is very much a bug!")

        val bucket = storageBuckets[type]!!
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        var errored = false

        val body = withContext(Dispatchers.IO) { Buffer.buffer(data.use { stream -> stream.readAllBytes() }) }

        val success = exponentiallyBackoff(64000, 8) { attempt ->
            logger.trace(
                "[{}${clientInfo?.userUID}] Attempting to store {} as {} in gs://{}/{}; Attempt {}",
                clientInfo?.userUID,
                name,
                type,
                bucket,
                fullPath
            )
            val response = webClient.postAbs(
                    "https://www.googleapis.com/upload/storage/v1/b/$bucket/o?uploadType=media&name=${URLEncoder.encode(
                        fullPath,
                        "UTF-8"
                    )}"
                )
                .putHeader("Content-Type", mimeType)
                .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                .sendBufferAwait(body)

            when (response.statusCode()) {
                200 -> {
                    if (type in publicStorageTypes) {
                        if (makePublic(fullPath, bucket, clientInfo))
                            logger.trace("[{}] Made {} public in {}", clientInfo?.userUID, fullPath, bucket)
                        else
                            logger.trace("[{}] Failed to make {} public in {}", clientInfo?.userUID, fullPath, bucket)
                    }

                    return@exponentiallyBackoff false
                }
                400 -> {
                    logger.error(
                        "[{}] Got back response code 400 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L)
                        logger.error(
                            "[{}] Got back response code 401; reloading token and trying again",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )
                    else
                        logger.error(
                            "[{}] Got back response code 401 with data {}; reloading token and trying again",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )

                    reload()
                    return@exponentiallyBackoff true
                }
                403 -> {
                    logger.error(
                        "[{}] Got back response code 403 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    logger.error(
                        "[{}] Got back response code 404 with data {}; That is *really* bad; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    logger.warn(
                        "[{}] Got back response code {} with data {}; backing off and trying again",
                        clientInfo?.userUID,
                        response.statusCode(),
                        response.bodyAsString()
                    )
                    return@exponentiallyBackoff true
                }
            }
        } && !errored

        return success
    }

    override suspend fun provide(name: String, type: EnumStorageType, clientInfo: ClientInfo?): DataSource? {
        val bucket = storageBuckets.getValue(type)
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        if (isPublic(fullPath, bucket))
            return HTTPDataSource(URL("https://storage.googleapis.com/$bucket/$fullPath"))

        if (doesObjectExist(fullPath, bucket, clientInfo)) {
            if (type in publicStorageTypes) {
                if (makePublic(fullPath, bucket, null)) {
                    logger.trace("Made {} public in {}", fullPath, bucket)
                    return HTTPDataSource(URL("https://storage.googleapis.com/$bucket/$fullPath"))
                } else
                    logger.trace("Failed to make {} public in {}", fullPath, bucket)
            }

            var data: Buffer? = null

            val success = exponentiallyBackoff(64000, 8) { attempt ->
                logger.trace(
                    "[{}] Attempting to download and stream gs://{}/{} exists; Attempt {}",
                    clientInfo?.userUID,
                    bucket,
                    fullPath,
                    attempt
                )
                val response = webClient.getAbs(
                        "https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(
                            fullPath,
                            "UTF-8"
                        )}?alt=media"
                    )
                    .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                    .sendAwait()

                when (response.statusCode()) {
                    200 -> {
                        data = response.body()
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        logger.error(
                            "[{}] Got back response code 400 with data {}; returning",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )
                        data = null
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        if (attempt == 0L) {
                            logger.error(
                                "[{}] Got back response code 401; reloading token and trying again",
                                clientInfo?.userUID
                            )
                            reload()
                            return@exponentiallyBackoff true
                        } else {
                            logger.error(
                                "[{}] Got back response code 401 with data {}; returning.",
                                clientInfo?.userUID,
                                response.bodyAsString()
                            )
                            data = null
                            return@exponentiallyBackoff false
                        }
                    }
                    403 -> {
                        logger.error(
                            "[{}] Got back response code 403 with data {}; returning",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )
                        data = null
                        return@exponentiallyBackoff false
                    }
                    404 -> {
                        logger.error(
                            "[{}] Got back response code 404 with data {}; That is *really* bad; returning",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )
                        data = null
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        logger.warn(
                            "[{}] Got back response code {} with data {}; backing off and trying again",
                            clientInfo?.userUID,
                            response.statusCode(),
                            response.bodyAsString()
                        )
                        return@exponentiallyBackoff true
                    }
                }
            } && data != null

            if (success)
                return BufferDataSource(data!!)
            return null
        }

        return null
    }

    override suspend fun provide(
        name: String,
        type: EnumStorageType,
        context: RoutingContext,
        clientInfo: ClientInfo?
    ): Boolean {
        val bucket = storageBuckets.getValue(type)
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        if (isPublic(fullPath, bucket)) {
            context.response().redirect("https://storage.googleapis.com/$bucket/$fullPath")
            return true
        }

        if (doesObjectExist(fullPath, bucket, clientInfo)) {
            if (type in publicStorageTypes) {
                if (makePublic(fullPath, bucket, null)) {
                    logger.trace("Made {} public in {}", fullPath, bucket)
                    context.response().redirect("https://storage.googleapis.com/$bucket/$fullPath")
                    return true
                } else
                    logger.trace("Failed to make {} public in {}", fullPath, bucket)
            }

            var response: HttpResponse<Buffer>? = null

            val success = exponentiallyBackoff(64000, 8) { attempt ->
                logger.trace(
                    "[{}] Attempting to download and stream gs://{}/{} exists; Attempt {}",
                    clientInfo?.userUID,
                    bucket,
                    fullPath,
                    attempt
                )
                val localResponse = webClient.getAbs(
                        "https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(
                            fullPath,
                            "UTF-8"
                        )}?alt=media"
                    )
                    .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                    .sendAwait()

                when (localResponse.statusCode()) {
                    200 -> {
                        response = localResponse
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        logger.error(
                            "[{}] Got back response code 400 with data {}; returning",
                            clientInfo?.userUID,
                            localResponse.bodyAsString()
                        )
                        response = null
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        if (attempt == 0L) {
                            logger.error(
                                "[{}] Got back response code 401; reloading token and trying again",
                                clientInfo?.userUID
                            )
                            reload()
                            return@exponentiallyBackoff true
                        } else {
                            logger.error(
                                "[{}] Got back response code 401 with data {}; returning.",
                                clientInfo?.userUID,
                                localResponse.bodyAsString()
                            )
                            response = null
                            return@exponentiallyBackoff false
                        }
                    }
                    403 -> {
                        logger.error(
                            "[{}] Got back response code 403 with data {}; returning",
                            clientInfo?.userUID,
                            localResponse.bodyAsString()
                        )
                        response = null
                        return@exponentiallyBackoff false
                    }
                    404 -> {
                        logger.error(
                            "[{}] Got back response code 404 with data {}; That is *really* bad; returning",
                            clientInfo?.userUID,
                            localResponse.bodyAsString()
                        )
                        response = null
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        logger.warn(
                            "[{}] Got back response code {} with data {}; backing off and trying again",
                            clientInfo?.userUID,
                            localResponse.statusCode(),
                            localResponse.bodyAsString()
                        )
                        return@exponentiallyBackoff true
                    }
                }
            }

            if (success && response != null) {
                val clientResponse = context.response()
                clientResponse.headers().addAll(response!!.headers())
                clientResponse.end(response!!.body())
                return true
            }

            return false
        }

        return false
    }

    override suspend fun isStored(name: String, type: EnumStorageType): Boolean {
        if (!shouldStore(type))
            return false

        val bucket = storageBuckets[type]!!
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        val publicResponse = webClient.headAbs("https://storage.googleapis.com/$bucket/$fullPath")
            .followRedirects(false)
            .sendAwait()
        if (publicResponse.statusCode() == 200)
            return true

        val privateResponse = webClient.headAbs("https://storage.googleapis.com/$bucket/$fullPath")
            .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
            .sendAwait()

        if (publicResponse.statusCode() == 200) {
            if (doesObjectExist(fullPath, bucket, null)) {
                if (type in publicStorageTypes) {
                    if (makePublic(fullPath, bucket, null))
                        logger.trace("Made {} public in {}", fullPath, bucket)
                    else
                        logger.trace("Failed to make {} public in {}", fullPath, bucket)
                }

                return true
            }
        }

        return false
    }

    suspend fun isPublic(path: String, bucket: String): Boolean {
        val publicResponse = webClient.headAbs("https://storage.googleapis.com/$bucket/$path")
            .followRedirects(false)
            .sendAwait()

        return (publicResponse.statusCode() == 200)
    }

    suspend fun makePublic(path: String, bucket: String, clientInfo: ClientInfo?): Boolean {
        if (isPublic(path, bucket)) return true

        var errored = false

        return exponentiallyBackoff(64000, 8) { attempt ->
            logger.trace(
                "[{}] Attempting to make gs://{}/{} public; Attempt {}",
                clientInfo?.userUID,
                bucket,
                path,
                attempt
            )

            val response = webClient.postAbs(
                    "https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(
                        path,
                        "UTF-8"
                    )}/acl"
                )
                .putHeader("Content-Type", "application/json")
                .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                .sendJsonAwait(mapOf("entity" to "allUsers", "role" to "READER"))


            when (response.statusCode()) {
                200 -> return@exponentiallyBackoff false
                400 -> {
                    logger.error(
                        "[{}] Got back response code 400 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L) {
                        logger.error(
                            "[{}] Got back response code 401; reloading token and trying again",
                            clientInfo?.userUID
                        )

                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        logger.trace(
                            "[{}] Got back response code 401 with data {}; returning",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )

                        return@exponentiallyBackoff false
                    }
                }
                403 -> {
                    logger.error(
                        "[{}] Got back response code 403 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    logger.error(
                        "[{}] Got back response code 404 with data {}; That is *really* bad; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    logger.warn(
                        "[{}] Got back response code {} with data {}; backing off and trying again",
                        clientInfo?.userUID,
                        response.statusCode(),
                        response.bodyAsString()
                    )
                    return@exponentiallyBackoff true
                }
            }
        } && !errored
    }

    suspend fun doesObjectExist(path: String, bucket: String, clientInfo: ClientInfo?): Boolean {
        var errored = false

        val success = exponentiallyBackoff(64000, 8) { attempt ->
            logger.trace(
                "[{}] Attempting to check if gs://{}/{} exists; Attempt {}",
                clientInfo?.userUID,
                bucket,
                path,
                attempt
            )
            val response =
                webClient.headAbs("https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(path, "UTF-8")}")
                    .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                    .sendAwait()

            when (response.statusCode()) {
                200 -> return@exponentiallyBackoff false
                400 -> {
                    logger.error(
                        "[{}] Got back response code 400 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L) {
                        logger.error(
                            "[{}] Got back response code 401; reloading token and trying again",
                            clientInfo?.userUID
                        )
                        reload()
                        return@exponentiallyBackoff true
                    } else {
                        logger.error(
                            "[{}] Got back response code 401 with data {}; reloading token and trying again",
                            clientInfo?.userUID,
                            response.bodyAsString()
                        )
                        return@exponentiallyBackoff false
                    }
                }
                403 -> {
                    logger.error(
                        "[{}] Got back response code 403 with data {}; returning",
                        clientInfo?.userUID,
                        response.bodyAsString()
                    )
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    logger.error(
                        "[{}] Got back response code {} with data {}; backing off and trying again",
                        clientInfo?.userUID,
                        response.statusCode(),
                        response.bodyAsString()
                    )
                    return@exponentiallyBackoff true
                }
            }
        } && !errored

        return success
    }

    suspend fun reload() {
        accessTokenLock.writeAwait {
            val now = Instant.now().toEpochMilli()
            val token = JWT.create().withIssuer(serviceEmail)
                .withClaim("scope", "https://www.googleapis.com/auth/devstorage.full_control")
                .withAudience("https://www.googleapis.com/oauth2/v4/token")
                .withExpiresAt(Date(now + 1 * 60 * 60 * 1000))
                .withIssuedAt(Date(now)).sign(algorithm)
            var error: Boolean = false

            val success = exponentiallyBackoff(16000, 8) { attempt ->
                logger.trace("Attempting to reload Google Storage token; Attempt {}", attempt)

                val response = webClient.postAbs("https://www.googleapis.com/oauth2/v4/token")
                    .sendFormAwait(
                        MultiMap.caseInsensitiveMultiMap().addAll(
                            mapOf(
                                "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                "assertion" to token
                            )
                        )
                    )

                when (response.statusCode()) {
                    200 -> {
                        val tokenResponse = response.bodyAsJsonObject()

                        googleAccessToken = tokenResponse.getString("access_token")
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        logger.error("Got back response code 400 with data {}; returning", response.bodyAsString())
                        error = true
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        logger.error(
                            "Got back response code 401 with data {}; returning and setting token to null",
                            response.bodyAsString()
                        )
                        error = true
                        googleAccessToken = null
                        return@exponentiallyBackoff false
                    }
                    403 -> {
                        logger.error(
                            "Got back response code 403 with data {}; returning and setting token to null",
                            response.bodyAsString()
                        )
                        error = true
                        googleAccessToken = null
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        logger.warn(
                            "Got back response code {} with data {}; backing off and trying again",
                            response.statusCode(),
                            response.bodyAsString()
                        )
                        return@exponentiallyBackoff true
                    }
                }
            }

            if (success && !error)
                logger.info("Successfully reloaded Google Storage token")
            else
                logger.warn("Failed to reload Google Storage token")
        }
    }

    init {
        var privateKey = storageOptions["PRIVATE_KEY"]?.toString()
            ?: throw IllegalArgumentException("No private key provided!")
        if (File(privateKey).exists())
            privateKey = File(privateKey).readText()
        privateKey = privateKey.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "").trim()

        serviceEmail =
            storageOptions["SERVICE_EMAIL"]?.toString() ?: throw IllegalArgumentException("No service email provided!")

        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))
        val kf = KeyFactory.getInstance("RSA")
        algorithm = Algorithm.RSA256(null, kf.generatePrivate(keySpec) as RSAPrivateKey)

        val defaultBucket = storageOptions["DEFAULT_BUCKET"]?.toString()

        storageBuckets = EnumStorageType.values()
            .map { type ->
                type to (EternalJukebox.config.storageOptions["${type.name}_BUCKET"] as? String ?: defaultBucket)
            }
            .filter { ab -> ab.second != null }
            .map { (a, b) -> a to b!! }
            .toMap()

        storageFolderPaths = EnumStorageType.values()
            .map { type -> type to (EternalJukebox.config.storageOptions["${type.name}_FOLDER"] as? String ?: "") }
            .toMap()


        storageSupportsCors = ArrayList()

        GlobalScope.launch {
            reload()

            val corsTypes = publicStorageTypes.filter { storageType ->
                val bucket = storageBuckets[storageType] ?: return@filter false
                var errored = false
                val success = exponentiallyBackoff(64000, 8) { attempt ->
                    logger.info("Attempting to get {} to support CORS; Attempt {}", bucket, attempt)

                    val response = webClient.patchAbs("https://www.googleapis.com/storage/v1/b/$bucket")
                        .putHeader("Content-Type", "application/json")
                        .bearerTokenAuthentication(accessTokenLock.readAwait { googleAccessToken })
                        .sendJsonAwait(
                            mapOf(
                                "cors" to arrayOf(
                                    mapOf(
                                        "method" to arrayOf(
                                            "*"
                                        ), "origin" to arrayOf("*")
                                    )
                                )
                            )
                        )

                    when (response.statusCode()) {
                        200 -> return@exponentiallyBackoff false
                        400 -> {
                            logger.error("Got back response code 400 with data {}; returning", response.bodyAsString())
                            errored = true
                            return@exponentiallyBackoff false
                        }
                        401 -> {
                            if (attempt == 0L) {
                                logger.error("Got back response code 401; reloading token and trying again")
                                reload()
                                return@exponentiallyBackoff true
                            } else {
                                logger.error(
                                    "Got back response code 401 with data {}; reloading token and trying again",
                                    response.bodyAsString()
                                )
                                return@exponentiallyBackoff false
                            }
                        }
                        403 -> {
                            logger.error("Got back response code 403 with data {}; returning", response.bodyAsString())
                            errored = true
                            return@exponentiallyBackoff false
                        }
                        404 -> {
                            errored = true
                            return@exponentiallyBackoff false
                        }
                        else -> {
                            logger.warn(
                                "Got back response code {} with data {}; backing off and trying again",
                                response.statusCode(),
                                response.bodyAsString()
                            )
                            return@exponentiallyBackoff true
                        }
                    }
                } && !errored

                return@filter success
            }
            storageSupportsCors.addAll(corsTypes)
        }
    }


    suspend inline fun <T> ReentrantReadWriteLock.readAwait(crossinline action: suspend () -> T): T =
        withContext(tokenContext) {
            val rl = readLock()
            rl.lock()
            try {
                action()
            } finally {
                rl.unlock()
            }
        }

    suspend inline fun <T> ReentrantReadWriteLock.writeAwait(crossinline action: suspend () -> T): T =
        withContext(tokenContext) {
            val rl = readLock()

            val readCount = if (writeHoldCount == 0) readHoldCount else 0
            repeat(readCount) { rl.unlock() }

            val wl = writeLock()
            wl.lock()
            try {
                action()
            } finally {
                repeat(readCount) { rl.lock() }
                wl.unlock()
            }
        }
}