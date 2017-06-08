package org.abimon.eternalJukebox.storage

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mashape.unirest.http.Unirest
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import org.abimon.eternalJukebox.config
import org.abimon.eternalJukebox.objects.EnumDataType
import org.abimon.visi.io.errPrintln
import org.abimon.visi.io.forceError
import org.abimon.visi.lang.isValue
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*


object GoogleStorage: IStorage {
    //val request: JSONObject
    val algorithm: Algorithm
    val serviceEmail: String
    val defaultBucket: String?
    val buckets: Map<EnumDataType, String>
    var expires: Instant = Instant.now()
    var bearer: String = ""
    val rng = Random() //For exponential backoff

    val httpClient: HttpClient = Vertx.vertx().createHttpClient()

    override fun isStored(name: String, type: EnumDataType): Boolean {
        reloadIfExpired()

        for(i in 0 until 6) {
            val (response, data) = httpClient.getAbs("https://www.googleapis.com/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode("$type/$name", "UTF-8")}").putHeader("Authorization", "Bearer $bearer").endAndWaitForBody()
            if(response.statusCode() == 401) {
                println("[GoogleStorage -> isStored($name, $type)] Google returned 401 with error ${String(data)}; reloading and retrying")
                reload()
                continue
            }
            else if(response.statusCode() == 404)
                return false
            else if(response.statusCode() == 429 || response.statusCode() >= 500) {
                println("[GoogleStorage -> isStored($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; backing off and retrying")
                Thread.sleep(Math.pow(2.0, (i + 1.0)).toLong() + rng.nextInt(1000))
                continue
            }
            else if(response.statusCode() != 200) {
                println("[GoogleStorage -> isStored($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; returning false")
                return false
            }

            return true
        }

        return false
    }

    override fun provideURL(name: String, type: EnumDataType): String? = if(isPublic(name, type)) "https://www.googleapis.com/download/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode("$type/$name", "UTF-8")}?alt=media" else null

    override fun provide(name: String, type: EnumDataType): InputStream? {
        if(isPublic(name, type))
            return URL("https://www.googleapis.com/download/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode("$type/$name", "UTF-8")}?alt=media").openStream()

        reloadIfExpired()

        for(i in 0 until 6) {
            val (response, data) = httpClient.getAbs("https://www.googleapis.com/download/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode("$type/$name", "UTF-8")}?alt=media").putHeader("Authorization", "Bearer $bearer").endAndWaitForBody()
            if(response.statusCode() == 401) {
                println("[GoogleStorage -> provide($name, $type)] Google returned 401 with error ${String(data)}; reloading and retrying")
                reload()
                continue
            }
            else if(response.statusCode() == 404)
                return null
            else if(response.statusCode() == 429 || response.statusCode() >= 500) {
                println("[GoogleStorage -> provide($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; backing off and retrying")
                Thread.sleep(Math.pow(2.0, (i + 1.0)).toLong() + rng.nextInt(1000))
                continue
            }
            else if(response.statusCode() != 200) {
                println("[GoogleStorage -> provide($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; returning null")
                return null
            }

            return ByteArrayInputStream(data)
        }

        return null
    }

    override fun store(name: String, type: EnumDataType, data: InputStream) {
        reloadIfExpired()
        val bytes = data.use { data.readBytes() }

        for(i in 0 until 6) {
            val request = Unirest.post("https://www.googleapis.com/upload/storage/v1/b/${getBucket(type)}/o").header("Authorization", "Bearer $bearer").queryString("uploadType", "media").queryString("name","$type/$name").body(bytes).asJson()
            if(request.status == 401) {
                println("[GoogleStorage -> store($name, $type)] Google returned 401 with error ${request.body}; reloading and retrying")
                reload()
                continue
            }
            else if(request.status == 429 || request.status >= 500) {
                println("[GoogleStorage -> store($name, $type)] Google returned ${request.status} with error ${request.body}; backing off and retrying")
                Thread.sleep(Math.pow(2.0, (i + 1.0)).toLong() + rng.nextInt(1000))
                continue
            }
            else if(request.status != 200) {
                println("[GoogleStorage -> store($name, $type)] Google returned ${request.status} with error ${request.body}; returning")
                break
            }

            if(type != EnumDataType.LOG && type != EnumDataType.PROFILE)
                makePublic("$type/$name", type)
            break
        }
    }

    private fun makePublic(name: String, type: EnumDataType) {
        if(type == EnumDataType.LOG || type == EnumDataType.PROFILE) //Double check
            return

        for(i in 0 until 6) {
            val (response, data) = httpClient.postAbs("https://www.googleapis.com/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode(name, "UTF-8")}/acl").putHeader("Authorization", "Bearer $bearer").endAndWaitForBody(JSONObject().put("entity", "allUsers").put("role", "READER"))

            response.handler {  }

            if(response.statusCode() == 401) {
                println("[GoogleStorage -> makePublic($name, $type)] Google returned 401 with error ${String(data)}; reloading and retrying")
                reload()
                continue
            }
            else if(response.statusCode() == 429 || response.statusCode() >= 500) {
                println("[GoogleStorage -> makePublic($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; backing off and retrying")
                Thread.sleep(Math.pow(2.0, (i + 1.0)).toLong() + rng.nextInt(1000))
                continue
            }
            else if(response.statusCode() != 200) {
                println("[GoogleStorage -> makePublic($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; returning")
                break
            }

            break
        }
    }

    private fun isPublic(name: String, type: EnumDataType): Boolean {
        if(type == EnumDataType.LOG || type == EnumDataType.PROFILE) //Double check
            return false

        for(i in 0 until 6) {
            val (response, data) = httpClient.getAbs("https://www.googleapis.com/storage/v1/b/${getBucket(type)}/o/${URLEncoder.encode("$type/$name", "UTF-8")}/acl/allUsers").putHeader("Authorization", "Bearer $bearer").endAndWaitForBody()
            if (response.statusCode() == 401) {
                println("[GoogleStorage -> isPublic($name, $type)] Google returned 401 with error ${String(data)}; reloading and retrying")
                reload()
                continue
            } else if (response.statusCode() == 400) {
                return false
            } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                println("[GoogleStorage -> isPublic($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; backing off and retrying")
                Thread.sleep(Math.pow(2.0, (i + 1.0)).toLong() + rng.nextInt(1000))
                continue
            } else if (response.statusCode() != 200) {
                println("[GoogleStorage -> isPublic($name, $type)] Google returned ${response.statusCode()} with error ${String(data)}; returning")
                return false
            }

            return true
        }

        return false
    }

    override fun shouldHandle(type: EnumDataType): Boolean = buckets.containsKey(type) || defaultBucket != null

    private fun getBucket(type: EnumDataType): String = buckets[type] ?: defaultBucket ?: throw IllegalStateException("Attempting to get bucket for $type; no bucket defined!")

    private fun reloadIfExpired() {
        if(Instant.now().isAfter(expires))
            reload()
    }

    private fun reload() {
        val now = Instant.now().toEpochMilli()
        val token = JWT.create().withIssuer(serviceEmail).withClaim("scope", "https://www.googleapis.com/auth/devstorage.full_control").withAudience("https://www.googleapis.com/oauth2/v4/token").withExpiresAt(Date(now + 1 * 60 * 60 * 1000)).withIssuedAt(Date(now)).sign(algorithm)
        val response = Unirest.post("https://www.googleapis.com/oauth2/v4/token").field("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer").field("assertion", token).asJson()

        if(response.status != 200)
            errPrintln("[Google Storage] Error Code ${response.status}, with response ${response.body}")
        else {
            val body = response.body.`object`
            expires = Instant.ofEpochSecond(Instant.now().epochSecond + body["expires_in"] as Int)
            bearer = body["access_token"] as String
        }
    }

    init {
        val options = config.storageOptions
        if(!options.containsKey("serviceEmail"))
            forceError("[Google Storage] Missing key serviceEmail, shutting down")
        if(!options.containsKey("privateKey"))
            forceError("[GoogleStorage] Missing key privateKey, shutting down")

        if(!options.containsKey("bucket") && !options.containsKey("default_bucket") && !options.containsKey("buckets"))
            forceError("[GoogleStorage] Missing one of bucket, default_bucket, or buckets; shutting down")

        if(options.containsKey("bucket"))
            defaultBucket = options["bucket"]
        else if(options.containsKey("default_bucket"))
            defaultBucket = options["default_bucket"]
        else
            defaultBucket = null

        val bucketsJson = JSONObject(options["buckets"] ?: "{}")
        buckets = bucketsJson.keySet()
                .filter { dataType -> EnumDataType::class.isValue(dataType.toUpperCase()) }
                .map { dataType -> EnumDataType.valueOf(dataType.toUpperCase()) to bucketsJson[dataType] as String }
                .toMap()

        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(options["privateKey"]!!.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s+".toRegex(), "")))
        val kf = KeyFactory.getInstance("RSA")
        algorithm = Algorithm.RSA256(null, kf.generatePrivate(keySpec) as RSAPrivateKey)

        serviceEmail = options["serviceEmail"]!!

        reload()
    }

    private fun HttpClientRequest.endAndWait(body: JSONObject): HttpClientResponse {
        putHeader("Content-Type", "application/json")
        var response: HttpClientResponse? = null
        handler { response = it }
        end(body.toString())

        while(response == null) Thread.sleep(100)

        return response!!
    }

    private fun HttpClientRequest.endAndWaitForBody(): Pair<HttpClientResponse, ByteArray> {
        var response: HttpClientResponse? = null
        var data: ByteArray? = null
        handler {
            it.bodyHandler { data = it.bytes }
            response = it
        }
        end()

        while(response == null || data == null) Thread.sleep(100)

        return response!! to data!!
    }

    private fun HttpClientRequest.endAndWaitForBody(body: JSONObject): Pair<HttpClientResponse, ByteArray> {
        putHeader("Content-Type", "application/json")
        var response: HttpClientResponse? = null
        var data: ByteArray? = null
        handler {
            it.bodyHandler { data = it.bytes }
            response = it
        }
        end(body.toString())

        while(response == null || data == null) Thread.sleep(100)

        return response!! to data!!
    }
}


