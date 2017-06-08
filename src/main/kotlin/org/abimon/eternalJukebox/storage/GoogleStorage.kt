package org.abimon.eternalJukebox.storage

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mashape.unirest.http.Unirest
import org.abimon.eternalJukebox.config
import org.abimon.eternalJukebox.objects.EnumDataType
import org.abimon.visi.io.errPrintln
import org.abimon.visi.io.forceError
import java.io.InputStream
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.*


object GoogleStorage: IStorage {
    //val request: JSONObject
    val algorithm: Algorithm
    val serviceEmail: String
    var expires: Instant = Instant.now()
    var bearer: String = ""

    override fun isStored(name: String, type: EnumDataType): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun provide(name: String, type: EnumDataType): InputStream? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun store(name: String, type: EnumDataType, data: InputStream) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shouldHandle(type: EnumDataType): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

        val keySpec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(options["privateKey"]!!.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s+".toRegex(), "")))
        val kf = KeyFactory.getInstance("RSA")
        algorithm = Algorithm.RSA256(null, kf.generatePrivate(keySpec) as RSAPrivateKey)

        serviceEmail = options["serviceEmail"]!!

        reload()
    }
}


