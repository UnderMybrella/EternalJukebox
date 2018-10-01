package org.abimon.eternalJukebox.data.storage

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.EnumStorageType
import org.abimon.eternalJukebox.objects.GoogleTokenOAuthResponse
import org.abimon.visi.io.ByteArrayDataSource
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.HTTPDataSource
import java.io.File
import java.io.OutputStream
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object GoogleStorage : IStorage {
    val serviceEmail: String
    val algorithm: Algorithm

    val backingToken: AtomicReference<String> = AtomicReference("")
    var googleAccessToken: String?
        get() = backingToken.get().takeIf(String::isNotBlank)
        set(value) {
            backingToken.set(value ?: "")
        }

    val storageBuckets: Map<EnumStorageType, String>
    val storageFolderPaths: Map<EnumStorageType, String>
    val storageSupportsCors: List<EnumStorageType>

    val publicStorageTypes = arrayOf(
            EnumStorageType.UPLOADED_AUDIO,
            EnumStorageType.ANALYSIS,
            EnumStorageType.AUDIO,
            EnumStorageType.EXTERNAL_ANALYSIS,
            EnumStorageType.EXTERNAL_AUDIO
    )

    override fun shouldStore(type: EnumStorageType): Boolean = googleAccessToken != null && !disabledStorageTypes.contains(type) && storageBuckets[type] != null

    override fun store(name: String, type: EnumStorageType, data: DataSource, mimeType: String, clientInfo: ClientInfo?): Boolean {
        if (!shouldStore(type))
            throw IllegalStateException("[${clientInfo?.userUID}] ERROR: Attempting to store $type, even though we've said not to store it! This is very much a bug!")

        val bucket = storageBuckets[type]!!
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        var errored = false

        val success = exponentiallyBackoff(64000, 8) { attempt ->
            log("[${clientInfo?.userUID}] Attempting to store $name as $type in gs://$bucket/$fullPath; Attempt $attempt")
            val (_, response) = Fuel.post("https://www.googleapis.com/upload/storage/v1/b/$bucket/o?uploadType=media&name=${URLEncoder.encode(fullPath, "UTF-8")}")
                    .header("Content-Type" to mimeType, "Authorization" to "Bearer $googleAccessToken")
                    .apply { bodyCallback = data.bodyCallback }
                    .response()

            when (response.statusCode) {
                200 -> {
                    if (type in publicStorageTypes) {
                        if (makePublic(fullPath, bucket, clientInfo))
                            log("[${clientInfo?.userUID}] Made $fullPath public in $bucket")
                        else
                            log("[${clientInfo?.userUID}] Failed to make $fullPath public in $bucket")
                    }

                    return@exponentiallyBackoff false
                }
                400 -> {
                    log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L)
                        log("[${clientInfo?.userUID}] Got back response code 401; reloading token and trying again")
                    else
                        log("[${clientInfo?.userUID}] Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                    reload()
                    return@exponentiallyBackoff true
                }
                403 -> {
                    log("[${clientInfo?.userUID}] Got back response code 403 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    log("[${clientInfo?.userUID}] Got back response code 404 with data ${String(response.data)}; That is *really* bad; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && !errored

        return success
    }

    override fun provide(name: String, type: EnumStorageType, clientInfo: ClientInfo?): DataSource? {
        val bucket = storageBuckets[type]!!
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        if (isPublic(fullPath, bucket))
            return HTTPDataSource(URL("https://storage.googleapis.com/$bucket/$fullPath"))

        if (doesObjectExist(fullPath, bucket, clientInfo)) {
            if (type in publicStorageTypes) {
                if (makePublic(fullPath, bucket, null)) {
                    log("Made $fullPath public in $bucket")
                    return HTTPDataSource(URL("https://storage.googleapis.com/$bucket/$fullPath"))
                } else
                    log("Failed to make $fullPath public in $bucket")
            }

            var data: ByteArray? = null

            val success = exponentiallyBackoff(64000, 8) { attempt ->
                log("[${clientInfo?.userUID}] Attempting to download and stream gs://$bucket/$fullPath exists; Attempt $attempt")
                val (_, response) = Fuel.get("https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(fullPath, "UTF-8")}?alt=media")
                        .header("Authorization" to "Bearer $googleAccessToken")
                        .response()

                when (response.statusCode) {
                    200 -> {
                        data = response.data
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        if (attempt == 0L)
                            log("[${clientInfo?.userUID}] Got back response code 401; reloading token and trying again")
                        else
                            log("[${clientInfo?.userUID}] Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                        reload()
                        return@exponentiallyBackoff true
                    }
                    403 -> {
                        log("[${clientInfo?.userUID}] Got back response code 403 with data ${String(response.data)}; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    404 -> {
                        log("[${clientInfo?.userUID}] Got back response code 404 with data ${String(response.data)}; That is *really* bad; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                        return@exponentiallyBackoff true
                    }
                }
            } && data != null

            if (success)
                return ByteArrayDataSource(data!!)
            return null
        }

        return null
    }

    override fun provide(name: String, type: EnumStorageType, context: RoutingContext, clientInfo: ClientInfo?): Boolean {
        val bucket = storageBuckets[type]!!
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
                    log("Made $fullPath public in $bucket")
                    context.response().redirect("https://storage.googleapis.com/$bucket/$fullPath")
                    return true
                } else
                    log("Failed to make $fullPath public in $bucket")
            }

            var data: ByteArray? = null

            val success = exponentiallyBackoff(64000, 8) { attempt ->
                log("[${clientInfo?.userUID}] Attempting to download and stream gs://$bucket/$fullPath exists; Attempt $attempt")
                val (_, response) = Fuel.get("https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(fullPath, "UTF-8")}?alt=media")
                        .header("Authorization" to "Bearer $googleAccessToken")
                        .response()

                when (response.statusCode) {
                    200 -> {
                        data = response.data
                        return@exponentiallyBackoff false
                    }
                    400 -> {
                        log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        if (attempt == 0L)
                            log("[${clientInfo?.userUID}] Got back response code 401; reloading token and trying again")
                        else
                            log("[${clientInfo?.userUID}] Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                        reload()
                        return@exponentiallyBackoff true
                    }
                    403 -> {
                        log("[${clientInfo?.userUID}] Got back response code 403 with data ${String(response.data)}; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    404 -> {
                        log("[${clientInfo?.userUID}] Got back response code 404 with data ${String(response.data)}; That is *really* bad; returning")
                        data = null
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                        return@exponentiallyBackoff true
                    }
                }
            } && data != null

            if (success) {
                context.response().end(Buffer.buffer(data))
                return true
            }

            return false
        }

        return false
    }

    override fun isStored(name: String, type: EnumStorageType): Boolean {
        if (!shouldStore(type))
            return false

        val bucket = storageBuckets[type]!!
        val fullPath = buildString {
            append(storageFolderPaths[type] ?: "")
            append(name)
        }

        val (_, publicResponse) = Fuel.head("https://storage.googleapis.com/$bucket/$fullPath").response()
        if (publicResponse.statusCode == 200)
            return true

        if (doesObjectExist(fullPath, bucket, null)) {
            if (type in publicStorageTypes) {
                if (makePublic(fullPath, bucket, null))
                    log("Made $fullPath public in $bucket")
                else
                    log("Failed to make $fullPath public in $bucket")
            }

            return true
        }

        return false
    }

    fun isPublic(path: String, bucket: String): Boolean {
        val (_, publicResponse) = Fuel.head("https://storage.googleapis.com/$bucket/$path").response()
        return (publicResponse.statusCode == 200)
    }

    fun makePublic(path: String, bucket: String, clientInfo: ClientInfo?): Boolean {
        var errored = false

        return exponentiallyBackoff(64000, 8) { attempt ->
            log("[${clientInfo?.userUID}] Attempting to make gs://$bucket/$path public; Attempt $attempt")
            val (_, response) = Fuel.post("https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(path, "UTF-8")}/acl")
                    .header("Content-Type" to "application/json", "Authorization" to "Bearer $googleAccessToken")
                    .body(EternalJukebox.jsonMapper.writeValueAsBytes(mapOf("entity" to "allUsers", "role" to "READER")))
                    .response()

            when (response.statusCode) {
                200 -> return@exponentiallyBackoff false
                400 -> {
                    log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L)
                        log("[${clientInfo?.userUID}] Got back response code 401; reloading token and trying again")
                    else
                        log("[${clientInfo?.userUID}] Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                    reload()
                    return@exponentiallyBackoff true
                }
                403 -> {
                    log("[${clientInfo?.userUID}] Got back response code 403 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    log("[${clientInfo?.userUID}] Got back response code 404 with data ${String(response.data)}; That is *really* bad; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && !errored
    }

    fun doesObjectExist(path: String, bucket: String, clientInfo: ClientInfo?): Boolean {
        var errored = false

        val success = exponentiallyBackoff(64000, 8) { attempt ->
            log("[${clientInfo?.userUID}] Attempting to check if gs://$bucket/$path exists; Attempt $attempt")
            val (_, response) = Fuel.head("https://www.googleapis.com/storage/v1/b/$bucket/o/${URLEncoder.encode(path, "UTF-8")}")
                    .header("Authorization" to "Bearer $googleAccessToken")
                    .response()

            when (response.statusCode) {
                200 -> return@exponentiallyBackoff false
                400 -> {
                    log("[${clientInfo?.userUID}] Got back response code 400 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    if (attempt == 0L)
                        log("[${clientInfo?.userUID}] Got back response code 401; reloading token and trying again")
                    else
                        log("[${clientInfo?.userUID}] Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                    reload()
                    return@exponentiallyBackoff true
                }
                403 -> {
                    log("[${clientInfo?.userUID}] Got back response code 403 with data ${String(response.data)}; returning")
                    errored = true
                    return@exponentiallyBackoff false
                }
                404 -> {
                    errored = true
                    return@exponentiallyBackoff false
                }
                else -> {
                    log("[${clientInfo?.userUID}] Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        } && !errored

        return success
    }

    fun reload() {
        val now = Instant.now().toEpochMilli()
        val token = JWT.create().withIssuer(serviceEmail).withClaim("scope", "https://www.googleapis.com/auth/devstorage.full_control").withAudience("https://www.googleapis.com/oauth2/v4/token").withExpiresAt(Date(now + 1 * 60 * 60 * 1000)).withIssuedAt(Date(now)).sign(algorithm)
        var error: Boolean = false

        val success = exponentiallyBackoff(16000, 8) { attempt ->
            log("Attempting to reload Google Storage token; Attempt $attempt")

            val (_, response) = Fuel.post(
                    "https://www.googleapis.com/oauth2/v4/token",
                    listOf("grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" to token)
            ).response()

            when (response.statusCode) {
                200 -> {
                    val tokenResponse = EternalJukebox.jsonMapper.tryReadValue(response.data, GoogleTokenOAuthResponse::class)
                            ?: run {
                                log("Unable to map Google token response; response data: ${String(response.data)}")
                                return@exponentiallyBackoff false
                            }

                    googleAccessToken = tokenResponse.access_token
                    return@exponentiallyBackoff false
                }
                400 -> {
                    log("Got back response code 400 with data ${String(response.data)}; returning")
                    error = true
                    return@exponentiallyBackoff false
                }
                401 -> {
                    log("Got back response code 401 with data ${String(response.data)}; returning and setting token to null")
                    error = true
                    googleAccessToken = null
                    return@exponentiallyBackoff false
                }
                403 -> {
                    log("Got back response code 403 with data ${String(response.data)}; returning and setting token to null")
                    error = true
                    googleAccessToken = null
                    return@exponentiallyBackoff false
                }
                else -> {
                    log("Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                    return@exponentiallyBackoff true
                }
            }
        }

        if (success && !error)
            log("Successfully reloaded Google Storage token")
        else
            log("Failed to reload Google Storage token")
    }

    init {
        var privateKey = storageOptions["PRIVATE_KEY"]?.toString()
                ?: throw IllegalArgumentException("No private key provided!")
        if (File(privateKey).exists())
            privateKey = File(privateKey).readText()
        privateKey = privateKey.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s+".toRegex(), "").trim()

        serviceEmail = storageOptions["SERVICE_EMAIL"]?.toString() ?: throw IllegalArgumentException("No service email provided!")

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

        reload()

        storageSupportsCors = publicStorageTypes.filter { storageType ->
            val bucket = storageBuckets[storageType] ?: return@filter false
            var errored = false
            val success = exponentiallyBackoff(64000, 8) { attempt ->
                log("Attempting to get $bucket to support CORS; Attempt $attempt")
                val (_, response) = Fuel.patch("https://www.googleapis.com/storage/v1/b/$bucket")
                        .header("Authorization" to "Bearer $googleAccessToken", "Content-Type" to "application/json")
                        .body(EternalJukebox.jsonMapper.writeValueAsBytes(mapOf("cors" to arrayOf(mapOf("method" to arrayOf("*"), "origin" to arrayOf("*"))))))
                        .response()

                when (response.statusCode) {
                    200 -> return@exponentiallyBackoff false
                    400 -> {
                        log("Got back response code 400 with data ${String(response.data)}; returning")
                        errored = true
                        return@exponentiallyBackoff false
                    }
                    401 -> {
                        if (attempt == 0L)
                            log("Got back response code 401; reloading token and trying again")
                        else
                            log("Got back response code 401 with data ${String(response.data)}; reloading token and trying again")

                        reload()
                        return@exponentiallyBackoff true
                    }
                    403 -> {
                        log("Got back response code 403 with data ${String(response.data)}; returning")
                        errored = true
                        return@exponentiallyBackoff false
                    }
                    404 -> {
                        errored = true
                        return@exponentiallyBackoff false
                    }
                    else -> {
                        log("Got back response code ${response.statusCode} with data ${String(response.data)}; backing off and trying again")
                        return@exponentiallyBackoff true
                    }
                }
            } && !errored

            return@filter success
        }
    }

    val DataSource.bodyCallback: (Request, OutputStream?, Long) -> Long
        get() = callback@{ request, outputStream, length ->
            if (outputStream == null)
                return@callback size
            else {
                use { stream -> stream.copyTo(outputStream) }
                return@callback size //Don't think it matters
            }
        }
}