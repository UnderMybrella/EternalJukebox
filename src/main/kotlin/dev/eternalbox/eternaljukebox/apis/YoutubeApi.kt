package dev.eternalbox.eternaljukebox.apis

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArrayResult
import dev.eternalbox.eternaljukebox.EternalJukebox
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.asTree
import dev.eternalbox.eternaljukebox.data.JukeboxResult
import dev.eternalbox.eternaljukebox.data.YoutubeVideo
import dev.eternalbox.eternaljukebox.data.resultForNullable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts

@ExperimentalCoroutinesApi
@ExperimentalContracts
class YoutubeApi(jukebox: EternalJukebox) {
    val apiKey = requireNotNull(jukebox["youtube_api_key"]).asText()

    suspend fun videoFor(id: String): JukeboxResult<YoutubeVideo> =
        Fuel.get(
            "https://www.googleapis.com/youtube/v3/videos",
            listOf("part" to "snippet", "id" to id, "key" to apiKey)
        ).awaitByteArrayResult()
            .asTree()
            .flatMap { json -> resultForNullable((json["items"] as ArrayNode).firstOrNull()) }
            .mapAwait { json -> withContext(Dispatchers.IO) { JSON_MAPPER.convertValue<YoutubeVideo>(json["snippet"]) } }
}