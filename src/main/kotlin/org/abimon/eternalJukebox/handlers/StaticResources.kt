package org.abimon.eternalJukebox.handlers

import io.vertx.ext.web.Cookie
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.handlers.api.ProfileAPI
import org.abimon.eternalJukebox.objects.ConstantValues

object StaticResources {

    fun setup(router: Router) {
        router.get("/profile.html").handler(this::profile)

        router.get().handler(StaticHandler.create(EternalJukebox.config.webRoot))
    }

    init {
        log("Initialised Static Resources")
    }

    fun profile(context: RoutingContext) {
        if (ProfileAPI.googleClient == null || ProfileAPI.googleSecret == null)
            return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(500).end {
                this["error"] = "Google Credentials not provided"
            }

        val authToken = context.clientInfo.authToken
        val account = authToken?.let { auth -> EternalJukebox.database.provideAccountForEternalAuth(auth, context.clientInfo) }

        if(account?.googleAccessToken != null && account.googleRefreshToken != null) {
            context.addCookie(Cookie.cookie(ConstantValues.AUTH_COOKIE_NAME, authToken).setPath("/"))
            return context.next()
        }

        val state = EternalJukebox.database.storeOAuthState(context.request().path(), context.clientInfo)

        context.response().redirect {
            append("https://accounts.google.com/o/oauth2/v2/auth?client_id=")
            append(ProfileAPI.googleClient)
            append("&state=$state&redirect_uri=")
            append(ProfileAPI.redirectURI)
            append("&response_type=code&scope=openid+profile&access_type=offline")

            if (account?.googleRefreshToken == null)
                append("&prompt=consent")
        }
    }
}