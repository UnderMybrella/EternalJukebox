package org.abimon.eternalJukebox

import org.abimon.eternalJukebox.objects.SpotifyAudioSegment

fun timbralDistance(seg1: SpotifyAudioSegment, seg2: SpotifyAudioSegment): Double = euclideanDistance(seg1.timbre, seg2.timbre)

fun euclideanDistance(timbre1: DoubleArray, timbre2: DoubleArray): Double = Math.sqrt((0 until 3)
        .map { timbre2[it] - timbre1[it] }
        .sumByDouble { it * it })

//fun preprocessTrack(track: EternalAudio) {
//    for(qlist in arrayOf(track.analysis.sections, track.analysis.bars, track.analysis.beats, track.analysis.tatums, track.analysis.segments)) {
//        for (i in qlist.indices) {
//            val q = qlist[i]
//            q.track = track
//            q.which = i
//            q.prev = if (i > 0) qlist[i - 1] else null
//            q.next = if (i < qlist.size - 1) qlist[i + 1] else null
//        }
//    }
//
//    connectQuanta(track, track.analysis.sections, track.analysis.bars)
//    connectQuanta(track, track.analysis.bars, track.analysis.beats)
//    connectQuanta(track, track.analysis.beats, track.analysis.tatums)
//    connectQuanta(track, track.analysis.tatums, track.analysis.sections)
//
//    connectFirstOverlappingSegment(track, track.analysis.bars)
//    connectFirstOverlappingSegment(track, track.analysis.beats)
//    connectFirstOverlappingSegment(track, track.analysis.tatums)
//
//    connectAllOverlappingSegments(track, track.analysis.bars)
//    connectAllOverlappingSegments(track, track.analysis.beats)
//    connectAllOverlappingSegments(track, track.analysis.tatums)
//
//    filterSegments(track)
//}
//
//fun connectQuanta(track: EternalAudio, parents: Array<out AnalysisType>, children: Array<out AnalysisType>) {
//    var last = 0
//
//    for(i in parents.indices) {
//        val parent = parents[i]
//        for(j in last until children.size) {
//            val child = children[j]
//
//            if(child.start >= parent.start && child.start < parent.start + parent.duration) {
//                child.parent = parent
//                child.indexInParent = parent.children.size
//                parent.children.add(child)
//                last = j
//            }
//            else if(child.start > parent.start)
//                break
//        }
//    }
//}
//
//fun connectFirstOverlappingSegment(track: EternalAudio, quanta: Array<out AnalysisType>) {
//    var last = 0
//    val segs = track.analysis.segments
//
//    for(i in quanta.indices) {
//        val q = quanta[i]
//
//        for(j in last until segs.size) {
//            val seg = segs[j]
//            if(seg.start >= q.start) {
//                q.oseg = seg
//                last = j
//                break
//            }
//        }
//    }
//}
//
//fun connectAllOverlappingSegments(track: EternalAudio, quanta: Array<out AnalysisType>) {
//    var last = 0
//    val segs = track.analysis.segments
//
//    for(i in quanta.indices) {
//        val q = quanta[i]
//
//        for(j in last until segs.size) {
//            val seg = segs[j]
//
//            if((seg.start + seg.duration) < q.start)
//                continue
//
//            if(seg.start > (q.start + q.duration))
//                break
//            last = j
//            q.overlappingSegments.add(seg)
//        }
//    }
//}
//
//fun filterSegments(track: EternalAudio) {
//    val threshold = 0.3
//    val fsegs = ArrayList<SpotifyAudioSegment>()
//    fsegs.add(track.analysis.segments[0])
//    for(i in 1 until track.analysis.segments.size) {
//        val seg = track.analysis.segments[i]
//        val last = fsegs.last()
//        if(isSimilar(seg, last) && seg.confidence < threshold)
//            last.duration += seg.duration
//        else
//            fsegs.add(seg)
//    }
//    track.analysis.fsegments = fsegs
//}
//
//fun isSimilar(seg1: SpotifyAudioSegment, seg2: SpotifyAudioSegment): Boolean = timbralDistance(seg1, seg2) < 1.0