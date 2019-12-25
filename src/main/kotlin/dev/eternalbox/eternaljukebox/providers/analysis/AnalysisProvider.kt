package dev.eternalbox.eternaljukebox.providers.analysis

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.eternalbox.eternaljukebox.JSON_MAPPER
import dev.eternalbox.eternaljukebox.data.*
import dev.eternalbox.eternaljukebox.data.spotify.SpotifyTrackAnalysis
import dev.eternalbox.eternaljukebox.json

interface AnalysisProvider {
    companion object {
        fun parseAnalysisData(data: ByteArray): JukeboxResult<JsonNode> {
            val jsonNode = try {
                JSON_MAPPER.readTree(data)
            } catch (jsonError: JsonParseException) {
                return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.INVALID_ANALYSIS_DATA,
                    WebApiResponseMessages.INVALID_ANALYSIS_DATA,
                    jsonError
                )
            }

            return parseAnalysisData(jsonNode)
        }

        fun parseAnalysisData(jsonNode: JsonNode): JukeboxResult<JsonNode> {
            val bars = findBars(jsonNode)
                ?: return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.ANALYSIS_MISSING_BARS,
                    WebApiResponseMessages.ANALYSIS_MISSING_BARS
                )
            val segments = findSegments(jsonNode)
                ?: return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.ANALYSIS_MISSING_SEGMENTS,
                    WebApiResponseMessages.ANALYSIS_MISSING_SEGMENTS
                )
            val beats = findBeats(jsonNode)
                ?: return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.ANALYSIS_MISSING_BEATS,
                    WebApiResponseMessages.ANALYSIS_MISSING_BEATS
                )
            val sections = findSections(jsonNode)
                ?: return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.ANALYSIS_MISSING_SECTIONS,
                    WebApiResponseMessages.ANALYSIS_MISSING_SECTIONS
                )
            val tatums = findTatums(jsonNode)
                ?: return JukeboxResult.KnownFailure(
                    WebApiResponseCodes.ANALYSIS_MISSING_TATUMS,
                    WebApiResponseMessages.ANALYSIS_MISSING_TATUMS
                )

            return collectAnalysisData(bars, beats, tatums, segments, sections)
        }

        fun parseAnalysisData(track: SpotifyTrackAnalysis): JukeboxResult<JsonNode> =
            collectAnalysisData(track.bars, track.beats, track.tatums, track.segments, track.sections)

        private fun collectAnalysisData(
            bars: Any,
            beats: Any,
            tatums: Any,
            segments: Any,
            sections: Any
        ): JukeboxResult<JsonNode> =
            JukeboxResult.Success(JSON_MAPPER.valueToTree(json {
                "bars" .. bars
                "beats" .. beats
                "tatums" .. tatums
                "segments" .. segments
                "sections" .. sections
            }))

        private fun findBars(tree: JsonNode): ArrayNode? {
            for (bars in tree.findValues("bars").filterIsInstance(ArrayNode::class.java)) {
                val firstBar = bars.firstOrNull() as? ObjectNode ?: continue
                if (firstBar.has("duration") && firstBar.has("start") && firstBar.has("confidence"))
                    return bars
            }

            return null
        }

        private fun findSegments(tree: JsonNode): ArrayNode? {
            for (segment in tree.findValues("segments").filterIsInstance(ArrayNode::class.java)) {
                val firstSegment = segment.firstOrNull() as? ObjectNode ?: continue
                if (firstSegment.has("confidence")
                    && firstSegment.has("timbre")
                    && firstSegment.has("pitches")
                    && firstSegment.has("start")
                    && firstSegment.has("duration")
                    && firstSegment.has("loudness_max_time")
                    && firstSegment.has("loudness_start")
                    && firstSegment.has("loudness_max")
                )
                    return segment
            }

            return null
        }

        private fun findBeats(tree: JsonNode): ArrayNode? {
            for (beats in tree.findValues("beats").filterIsInstance(ArrayNode::class.java)) {
                val firstBeat = beats.firstOrNull() as? ObjectNode ?: continue
                if (firstBeat.has("duration") && firstBeat.has("start") && firstBeat.has("confidence"))
                    return beats
            }

            return null
        }

        private fun findSections(tree: JsonNode): ArrayNode? {
            for (sections in tree.findValues("sections").filterIsInstance(ArrayNode::class.java)) {
                val firstSection = sections.firstOrNull() as? ObjectNode ?: continue
                if (firstSection.has("confidence")
                    && firstSection.has("mode_confidence")
                    && firstSection.has("time_signature")
                    && firstSection.has("key_confidence")
                    && firstSection.has("tempo")
                    && firstSection.has("time_signature_confidence")
                    && firstSection.has("start")
                    && firstSection.has("tempo_confidence")
                    && firstSection.has("mode")
                    && firstSection.has("key")
                    && firstSection.has("duration")
                    && firstSection.has("loudness")
                )
                    return sections
            }

            return null
        }

        private fun findTatums(tree: JsonNode): ArrayNode? {
            for (tatums in tree.findValues("tatums").filterIsInstance(ArrayNode::class.java)) {
                val firstTatum = tatums.firstOrNull() as? ObjectNode ?: continue
                if (firstTatum.has("duration") && firstTatum.has("start") && firstTatum.has("confidence"))
                    return tatums
            }

            return null
        }
    }

    suspend fun supportsAnalysis(service: EnumAnalysisService): Boolean
    suspend fun retrieveAnalysisFor(service: EnumAnalysisService, id: String): JukeboxResult<DataResponse>
}