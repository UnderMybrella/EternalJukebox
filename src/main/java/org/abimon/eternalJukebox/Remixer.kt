package org.abimon.eternalJukebox

import org.abimon.eternalJukebox.objects.EternalAudio
import org.abimon.eternalJukebox.objects.SpotifyAudioSegment

fun timbralDistance(seg1: SpotifyAudioSegment, seg2: SpotifyAudioSegment): Double = euclideanDistance(seg1.timbre, seg2.timbre)

fun euclideanDistance(timbre1: DoubleArray, timbre2: DoubleArray): Double = Math.sqrt((0 until 3)
        .map { timbre2[it] - timbre1[it] }
        .sumByDouble { it * it })

fun preprocessTrack(track: EternalAudio) {
    val qlist = track.analysis.sections

    for(i in qlist.indices) {

    }
}