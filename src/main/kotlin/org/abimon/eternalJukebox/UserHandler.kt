package org.abimon.eternalJukebox

import com.mashape.unirest.http.Unirest
import org.abimon.eternalJukebox.exceptions.HttpStatusCodeException
import org.abimon.eternalJukebox.objects.EternalUser
import org.abimon.eternalJukebox.objects.GoogleToken
import org.abimon.eternalJukebox.objects.GoogleUser

fun getGoogleUser(eternalUser: EternalUser): GoogleUser {
    try {
        if (System.currentTimeMillis() > eternalUser.expiresAt)
            refreshToken(eternalUser)
        return getGoogleUser(eternalUser.googleAccessToken)
    } catch(th: Throwable) {
        throw HttpStatusCodeException(401)
    }
}

fun getGoogleUser(accessToken: String): GoogleUser {
    val userResponse = Unirest.get("https://www.googleapis.com/plus/v1/people/me").header("Authorization", "Bearer $accessToken").asObject(GoogleUser::class.java)
    if (userResponse.status >= 400)
        throw HttpStatusCodeException(userResponse.status)

    return userResponse.body
}

fun refreshToken(eternalUser: EternalUser) {
    createOrUpdateUser(Unirest.post("https://www.googleapis.com/oauth2/v4/token").field("refresh_token", eternalUser.googleRefreshToken).field("client_id", config.googleClient).field("client_secret", config.googleSecret).field("grant_type", "refresh_token").asObject(GoogleToken::class.java).body)
}