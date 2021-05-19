package dev.eternalbox.analysis.spotify

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("spotify-credentials")
class SpotifyCredentials {
    lateinit var clientID: String
    lateinit var clientSecret: String
}