package dev.eternalbox.analysis.spotify

import dev.eternalbox.analysis.AnalysisApi
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
@ConditionalOnProperty(name = ["analysis-api"], havingValue = "Spotify")
@EnableConfigurationProperties(SpotifyCredentials::class)
@Controller
class SpotifyAnalysisApi(val clientID: String, val clientSecret: String, clientRegistrationRepository: ClientRegistrationRepository, authorisedClientService: OAuth2AuthorizedClientService): AnalysisApi {
    @Autowired
    constructor(credentials: SpotifyCredentials, clientRegistrationRepository: ClientRegistrationRepository, authorisedClientService: OAuth2AuthorizedClientService): this(credentials.clientID, credentials.clientSecret, clientRegistrationRepository, authorisedClientService)

    val spotifyClientRegistration = ClientRegistration.withRegistrationId("spotify")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("https://accounts.spotify.com/api/token")
            .clientName("EternalBox Spotify Api")
            .clientId(clientID)
            .clientSecret(clientSecret)
            .build()

    @Bean
    private fun webClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient {
        val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        return WebClient.builder()
                .apply(oauth2Client.oauth2Configuration())
                .build()
    }

    val authorizedClientManager: OAuth2AuthorizedClientManager by lazy {
        val authorisedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build()

        val authorisedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorisedClientService)
        authorisedClientManager.setAuthorizedClientProvider(authorisedClientProvider)

        authorisedClientManager
    }
    val client: WebClient by lazy { webClient(authorizedClientManager) }

    override suspend fun test() {
        client.get()
                .uri("https://google.com")
                .retrieve()
                .awaitBody<String>()
                .apply { println(this) }
        println("h-hewwo???")
        delay(10_000)
    }
}