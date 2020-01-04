package dev.eternalbox.eternaljukebox.routes

import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.bytesToHex
import dev.eternalbox.eternaljukebox.data.HttpResponseCodes
import dev.eternalbox.eternaljukebox.routeWith
import io.vertx.ext.auth.User
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.ext.auth.oauth2.OAuth2FlowType
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.OAuth2AuthHandler
import io.vertx.kotlin.ext.auth.oauth2.refreshAwait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class PatreonRoute(jukebox: EternalJukebox) : EternalboxRoute(jukebox) {
    val authProvider = OAuth2Auth.create(
        jukebox.vertx, OAuth2ClientOptions()
            .setFlow(OAuth2FlowType.AUTH_CODE)
            .setClientID(requireNotNull(jukebox["patreon_client_id"]).asText())
            .setClientSecret(requireNotNull(jukebox["patreon_client_secret"]).asText())
            .setAuthorizationPath("https://www.patreon.com/oauth2/authorize")
            .setTokenPath("https://www.patreon.com/api/oauth2/token")
    )

    val redirectURI = "${jukebox.host}/api/patreon/callback"

    val authorisation: MutableMap<User, Boolean> = ConcurrentHashMap()
    val authorisationLocks: MutableMap<User, Mutex> = ConcurrentHashMap()

    val campaignID = requireNotNull(jukebox["patreon_campaign_id"]).asText()
    val ownerID = requireNotNull(jukebox["patreon_owner_id"]).asText()
    val secretKey = SecretKeySpec(requireNotNull(jukebox["patreon_webhook_secret"]).asText().toByteArray(), "HmacMD5")
    val hmac = Mac.getInstance("HmacMD5")

    fun updatePatreon(context: RoutingContext) {
        try {
            val signature = context.request().getHeader("X-Patreon-Signature")
            val bodyHash = bytesToHex(hmac.doFinal(context.body.bytes))
            if (!signature.equals(bodyHash, true))
                return context.fail(403)

            //We're not investing a tonne of time into accounts atm, so just clear the cache
            authorisation.keys.forEach { user ->
                if (authorisationLocks[user]?.isLocked != true) {
                    authorisation.remove(user)
                }
            }

            context.response()
                .setStatusCode(HttpResponseCodes.NO_CONTENT)
                .end()
        } catch (th: Throwable) {
            th.printStackTrace()
            context.fail(th)
        }
    }

    suspend fun checkAuth(context: RoutingContext): Unit =
        routeWith(context) {
            if (user() == null) {
                return fail(401)
            } else {
                val user = user() as AccessToken
                if (user !in authorisation) {
                    authorisationLocks.computeIfAbsent(user) { Mutex() }.withLock {
                        if (user.expired()) {
                            try {
                                user.refreshAwait()
                            } catch (th: Throwable) {
                                clearUser()
                                return reroute(request().path())
                            }
                        }

                        val (data, error) = Fuel.get(
                            "https://www.patreon.com/api/oauth2/v2/identity",
                            listOf("include" to "memberships")
                        )
                            .header("Authorization", "Bearer ${user.opaqueAccessToken()}")
                            .awaitByteArrayResult()

                        if (data != null) {
                            val json = withContext(Dispatchers.IO) { JSON_MAPPER.readTree(data) }
                            val userID = json.get("data").get("id").asText()
                            if (userID == ownerID) {
                                authorisation[user] = true
                                return next()
                            }

                            val members = (json.get("data")
                                .get("relationships")
                                .get("memberships")
                                .get("data") as ArrayNode)
                                .filter { node -> node["type"].asText() == "member" }
                                .map { node -> node["id"].asText() }

                            authorisation[user] = false
                            if (members.isEmpty())
                                return fail(403)

                            for (memberID in members) {
                                val (memberData, memberError) = Fuel.get(
                                    "https://www.patreon.com/api/oauth2/v2/members/$memberID",
                                    listOf("include" to "currently_entitled_tiers,campaign")
                                ).header("Authorization", "Bearer ${user.opaqueAccessToken()}").awaitByteArrayResult()

                                if (memberData != null) {
                                    val memberJson = withContext(Dispatchers.IO) { JSON_MAPPER.readTree(memberData) }
                                    val memberCampaign = memberJson.get("data")
                                        ?.get("relationships")
                                        ?.get("campaign")
                                        ?.get("data")
                                        ?.get("id")
                                        ?.asText()

                                    if (memberCampaign == campaignID) {
                                        authorisation[user] = memberJson.get("data")
                                            ?.get("relationships")
                                            ?.get("currently_entitled_tiers")
                                            ?.get("data")
                                            ?.isEmpty
                                            ?.not()
                                            ?: false

                                        return next()
                                    }
                                }
                            }

                            return fail(403)
                        } else {
                            error?.printStackTrace()
                            return fail(500)
                        }
                    }
                } else if (authorisation.getValue(user)) {
                    return next()
                } else {
                    return fail(403)
                }
            }
        }

    init {
        hmac.init(secretKey)
        jukebox.sessionHandler.setAuthProvider(authProvider)

        jukebox.baseRouter.post("/api/patreon/update").order(-4_000_000).handler(BodyHandler.create().setBodyLimit(10_000).setDeleteUploadedFilesOnEnd(true))
        jukebox.baseRouter.post("/api/patreon/update").order(-4_000_000).handler(this::updatePatreon)

        val callbackRoute = jukebox.baseRouter.route().order(-4_000_000)
        jukebox.baseRouter.route().order(-2_000_000).handler(
            OAuth2AuthHandler.create(authProvider, redirectURI)
                .setupCallback(callbackRoute)
                .addAuthority("identity")
                .addAuthority("campaigns.members")
        )
        jukebox.baseRouter.route().order(-1_900_000).suspendHandler(this::checkAuth)
    }
}