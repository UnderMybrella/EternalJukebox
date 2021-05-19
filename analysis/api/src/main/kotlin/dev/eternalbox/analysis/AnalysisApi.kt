package dev.eternalbox.analysis

import dev.eternalbox.client.common.EternalboxTrack
import io.ktor.application.*
import io.ktor.routing.*

interface AnalysisApi {
    val service: String

    suspend fun getAnalysis(trackID: String): EternalboxTrack?
}

fun Application.setupAnalysisModule(api: AnalysisApi) {
    routing {
        route("/analysis") {
            route("/${api.service}") {

            }
        }
    }
}