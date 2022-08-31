package dev.eternalbox.httpclient

import dev.eternalbox.common.utils.TokenStore
import dev.eternalbox.common.utils.firstNotNull
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

public inline fun formDataContent(builder: ParametersBuilder.() -> Unit) =
    FormDataContent(Parameters.build(builder))

public suspend inline fun <T, reified R> TokenStore<T>.authorise(block: (T) -> HttpResponse): R? {
    var errors: Throwable? = null
    var authFailed = false
    for (i in 0 until 4) {
        try {
            val authToken = token.firstNotNull()
            val response = block(authToken)

            if (response.status.isSuccess()) {
                return response.body<R>()
            } else {
                when (response.status) {
                    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> {
                        if (authFailed) {
                            val th = IllegalStateException("Authorization for token failed, obtain new token")

                            errors =
                                if (errors == null) th
                                else th.initCause(errors)

                            continue
                        } else {
                            //Authorization failed, let's try again after we get our token working
                            authFailed = true
                            refreshToken(authToken)
                        }
                    }
                    HttpStatusCode.BadRequest -> {
//                        launch(Dispatchers.IO) {
//                            val parent = File("spotify/track_analysis/bad_request/")
//                            val file = File(parent, buildString {
//                                append(songID)
//                                parent.listFiles()?.count { file -> file.name.startsWith(songID) }?.let {
//                                    append('_')
//                                    append(it)
//                                }
//                                append(".json")
//                            })
//
//                            logger.error("Received a bad request from Spotify; delaying then trying again (Response written to ${file.name})")
//
//                            parent.mkdirs()
//                            file.writeText(response.receive())
//                        }

                        println("Received a bad request; delaying then trying again")
                        println(response.bodyAsText())

                        delay((2.0.pow(i).plus(Random.nextDouble()) * 1000).roundToLong())
                    }
                    HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> {
//                        logger.error("Spotify timed out; delaying then trying again")

                        delay((2.0.pow(i).plus(Random.nextDouble()) * 1000).roundToLong())
                    }

                    else -> {
//                        launch(Dispatchers.IO) {
//                            val parent = File("spotify/track_analysis/${response.status.value}/")
//                            val file = File(parent, buildString {
//                                append(songID)
//                                parent.listFiles()?.count { file -> file.name.startsWith(songID) }?.let {
//                                    append('_')
//                                    append(it)
//                                }
//                                append(".json")
//                            })
//
//                            logger.error("Don't know how to handle ${response.status}; delaying then trying again (Response written to ${file.name})")
//
//                            parent.mkdirs()
//                            file.writeText(response.receive())
//                        }


                        println("Received a bad request; delaying then trying again")
                        println(response.bodyAsText())

                        delay((2.0.pow(i).plus(Random.nextDouble()) * 1000).roundToLong())
                    }
                }
            }
        } catch (th: Throwable) {
            errors =
                if (errors == null) th
                else th.initCause(errors)
        }
    }

    throw errors!!
}