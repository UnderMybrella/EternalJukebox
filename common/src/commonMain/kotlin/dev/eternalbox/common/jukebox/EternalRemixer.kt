package dev.eternalbox.common.jukebox

import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sqrt

class EternalRemixer(
        private val maxBranchesPerBeat: Int = 4,
        private val maxBranchThreshold: Int = 80,

        private val addLastEdge: Boolean = true,
        private val justBackwards: Boolean = false,
        private val justLongBranches: Boolean = false,
        private val removeSequentialBranches: Boolean = false,

        val TIMBRE_WEIGHT: Double = 1.0,
        val PITCH_WEIGHT: Double = 10.0,
        val LOUDNESS_START_WEIGHT: Double = 1.0,
        val LOUDNESS_MAX_WEIGHT: Double = 1.0,
        val DURATION_WEIGHT: Double = 100.0,
        val CONFIDENCE_WEIGHT: Double = 1.0
) {

    data class Branch<T : JukeboxSegmentedType<T>>(val percent: Double, val index: Int, val which: Int, val q: T, val neighbour: JukeboxEdge<T>) : Comparable<Branch<T>> {
        override fun compareTo(other: Branch<T>): Int = percent.compareTo(other.percent)
    }

    /** jremix.js */
    fun preprocessTrack(track: JukeboxTrack) {
        track.sections.preprocessQuanta(track)
        track.bars.preprocessQuanta(track)
        track.beats.preprocessQuanta(track)
        track.tatums.preprocessQuanta(track)
        track.segments.preprocessQuanta(track)

        connectQuanta(track.sections, track.bars)
        connectQuanta(track.bars, track.beats)
        connectQuanta(track.beats, track.tatums)
        connectQuanta(track.tatums, track.segments)

        track.bars.connectFirstOverlappingSegment(track.segments)
        track.beats.connectFirstOverlappingSegment(track.segments)
        track.tatums.connectFirstOverlappingSegment(track.segments)

        track.bars.connectAllOverlappingSegment(track.segments)
        track.beats.connectAllOverlappingSegment(track.segments)
        track.tatums.connectAllOverlappingSegment(track.segments)

        track.filterSegments()
    }

    fun <T : JukeboxAnalysisType<T>> Array<T>.preprocessQuanta(track: JukeboxTrack) {
        forEachIndexed { index, q ->
            q.track = track
            q.which = index

            if (index > 0) {
                q.prev = this[index - 1]
            }

            if (index < size - 1) {
                q.next = this[index + 1]
            }
        }
    }

    fun <PARENT : JukeboxAnalysisType<PARENT>, CHILD : JukeboxAnalysisType<CHILD>> connectQuanta(qparents: Array<PARENT>, qchildren: Array<CHILD>) {
        var last = 0
        qparents.forEachIndexed { index, qparent ->
            qparent.children.clear()

            for (j in last until qchildren.size) {
                val qchild = qchildren[j]
                if (qchild.start >= qparent.start && qchild.start < qparent.end) {
                    qchild.parent = qparent
                    qchild.indexInParent = qparent.children.size
                    qparent.children.add(qchild)
                    last = j
                } else if (qchild.start > qparent.start) {
                    break
                }
            }
        }
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.connectFirstOverlappingSegment(segs: Array<JukeboxSegmentAnalysis>) {
        var last = 0

        forEach { q ->
            for (j in last until segs.size) {
                val qseg = segs[j]
                if (qseg.start >= q.start) {
                    q.oseg = qseg
                    last = j
                    break
                }
            }
        }
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.connectAllOverlappingSegment(segs: Array<JukeboxSegmentAnalysis>) {
        var last = 0

        forEach { q ->
            q.overlappingSegments.clear()

            for (j in last until segs.size) {
                val qseg = segs[j]

                //sic. Paul Lamere
                //seg starts before quantum so no
                if (qseg.end < q.start) {
                    continue
                }

                //sic. Paul Lamere
                //seg starts after quantum so no
                if (qseg.start > q.end) {
                    break
                }

                last = j
                q.overlappingSegments.add(qseg)
            }
        }
    }

    fun JukeboxTrack.filterSegments() {
        val threshold = 0.3
        filteredSegments.clear()
        filteredSegments.add(segments[0])

        for (i in 1 until segments.size) {
            val seg = segments[i]
            val last = filteredSegments.last()

            if (isSimilar(seg, last) && seg.confidence < threshold) {
                filteredSegments[filteredSegments.size - 1] = last.copy(duration = last.duration + seg.duration)
            } else {
                filteredSegments.add(seg)
            }
        }
    }

    fun isSimilar(first: JukeboxSegmentAnalysis, second: JukeboxSegmentAnalysis): Boolean {
        val threshold = 1.0
        val distance = timbralDistance(first, second)
        return distance < threshold
    }

    fun timbralDistance(first: JukeboxSegmentAnalysis, second: JukeboxSegmentAnalysis) =
            euclideanDistance(first.timbre, second.timbre, 3) //¯\_(ツ)_/¯

    fun getSegDistances(seg1: JukeboxSegmentAnalysis, seg2: JukeboxSegmentAnalysis): Double {
        val timbre = weightedEuclideanDistance(seg1.timbre, seg2.timbre) * TIMBRE_WEIGHT
        val pitch = euclideanDistance(seg1.pitches, seg2.pitches) * PITCH_WEIGHT
        val sloudStart = abs(seg1.loudnessStart - seg2.loudnessStart) * LOUDNESS_START_WEIGHT
        val sloudMax = abs(seg1.loudnessMax - seg2.loudnessMax) * LOUDNESS_MAX_WEIGHT
        val duration = abs(seg1.duration - seg2.duration) * DURATION_WEIGHT
        val confidence = abs(seg1.confidence - seg2.confidence) * CONFIDENCE_WEIGHT

        return timbre + pitch + sloudStart + sloudMax + duration + confidence
    }

    fun euclideanDistance(v1: List<Double>, v2: List<Double>, cap: Int = minOf(v1.size, v2.size)): Double {
        var sum = 0.0
        for (i in 0 until cap) {
            val delta = v2[i] - v1[i]
            sum += delta * delta
        }

        return sqrt(sum)
    }

    fun weightedEuclideanDistance(v1: List<Double>, v2: List<Double>): Double {
        var sum = 0.0
        // for (i in 0 until 4)
        for (i in v1.indices) {
            val delta = v2[i] - v1[i]
            //var weight = 1.0 / (i + 1.0)
            val weight = 1.0
            sum += delta * delta * weight
        }

        return sqrt(sum)
    }

    /** go.js */
    fun processBranches(track: JukeboxTrack) {
        track.beats.dynamicallyCalculateNearestNeighbours()
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.dynamicallyCalculateNearestNeighbours(): Int {
        var count = 0
        val targetBranchCount = size / 6
        precalculateNearestNeighbours(maxBranchesPerBeat, maxBranchThreshold)

        var threshold = 10
        val thresholdRange = 10 until maxBranchThreshold
        while (threshold in thresholdRange) {
            count = collectNearestNeighbours(threshold)
            if (count >= targetBranchCount) break
            threshold += 5
        }

        //jukeboxData.computedThreshold = threshold
        //jukeboxData.currentThreshold = jukeboxData.computedThreshold

        postProcessNearestNeighbours(threshold)
        return count
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.precalculateNearestNeighbours(maxNeighbours: Int, maxThreshold: Int) {
        if (this[0].allNeighbours.isNotEmpty()) return

        //TODO: jukeboxData.allEdges
        forEach { q -> calculateNearestNeighboursForQuantum(maxNeighbours, maxThreshold, q) }
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.calculateNearestNeighboursForQuantum(maxNeighbours: Int, maxThreshold: Int, q1: T) {
        var id = 0
        val edges: MutableList<JukeboxEdge<T>> = ArrayList()
        forEachIndexed { index, q2 ->
            if (index == q1.which) return@forEachIndexed

            var sum = 0.0
            q1.overlappingSegments.forEachIndexed { osegIndex, seg1 ->
                var distance = 100.0
                if (osegIndex < q2.overlappingSegments.size) {
                    val seg2 = q2.overlappingSegments[osegIndex]
                    //sic. Paul Lamere
                    // some segments can overlap many quantums,
                    // we don't want this self segue, so give them a
                    // high distance
                    if (seg1.which == seg2.which) {
                        distance = 100.0
                    } else {
                        distance = getSegDistances(seg1, seg2)
                    }
                }

                sum += distance
            }

            val pdistance = if (q1.indexInParent == q2.indexInParent) 0.0 else 100.0
            val totalDistance = sum / q1.overlappingSegments.size.toDouble() + pdistance
            if (totalDistance < maxThreshold) {
                edges.add(JukeboxEdge(id++, q1, q2, totalDistance))
            }
        }

//        edges.sortWith(Comparator { a, b -> if (a.distance > b.distance) 1 else if (b.distance > a.distance) -1 else 0 })
        edges.sort()

        q1.allNeighbours.clear()
        q1.allNeighbours.addAll(edges.take(maxNeighbours))
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.collectNearestNeighbours(maxThreshold: Int): Int {
        var branchingCount = 0

        forEach { q ->
            q.extractNearestNeighbours(maxThreshold)
            if (q.neighbours.isNotEmpty())
                branchingCount++
        }

        return branchingCount
    }

    fun <T : JukeboxSegmentedType<T>> T.extractNearestNeighbours(maxThreshold: Int) {
        neighbours.clear()

        allNeighbours.forEach { neighbour ->
            //if (neighbour.deleted) return@forEach

            if (justBackwards && neighbour.dest.which > which) return@forEach

            if (justLongBranches && abs(neighbour.dest.which - which) < track.beats.size / 5) return@forEach

            if (neighbour.distance <= maxThreshold)
                neighbours.add(neighbour)
        }
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.postProcessNearestNeighbours(currentThreshold: Int) {
        //removeDeletedEdges()

        if (addLastEdge) {
            if (longestBackwardBranch() < 50.0) {
                insertBestBackwardBranch(currentThreshold, 65)
            } else {
                insertBestBackwardBranch(currentThreshold, 55)
            }
        }

        calculateReachability()
        val lastBranchPoint = findBestLastBeat()
        filterOutBadBranches(lastBranchPoint)
        if (removeSequentialBranches) filterOutSequentialBranches(lastBranchPoint)
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.longestBackwardBranch(): Double {
        var longest = 0
        forEachIndexed { index, q ->
            q.neighbours.forEachIndexed { neighbourIndex, neighbour ->
                val delta = index - neighbour.dest.which
                if (delta > longest)
                    longest = delta
            }
        }

        return longest * 100.0 / size.toDouble()
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.insertBestBackwardBranch(threshold: Int, maxThreshold: Int) {
        val branches: MutableList<Branch<T>> = ArrayList()

        forEachIndexed { index, q ->
            q.allNeighbours.forEachIndexed inner@{ neighbourIndex, neighbour ->
//                if (neighbour.deleted) return@inner

                val delta = index - neighbour.dest.which
                if (delta > 0 && neighbour.distance < maxThreshold) {
                    branches.add(Branch(delta * 100.0 / size.toDouble(), index, neighbour.dest.which, q, neighbour))
                }
            }
        }

        branches.sort()
        branches.reverse()

        val best = branches.first()

        if (best.neighbour.distance > threshold) {
            best.q.neighbours.add(best.neighbour)
        }
    }

    fun <T : JukeboxSegmentedType<T>> Array<T>.calculateReachability() {
        val maxIter = 1000
        var iter = 0
        forEach { q -> q.reach = size - q.which }

        while (iter < maxIter) {
            var changeCount = 0
            forEachIndexed { qi, q ->
                var changed = false
                q.neighbours.forEach { q2 ->
                    if (q2.dest.reach > q.reach) {
                        q.reach = q2.dest.reach
                        changed = true
                    }
                }

                if (qi < size - 1) {
                    val q2 = last()
                    if (q2.reach > q.reach) {
                        q.reach = q2.reach
                        changed = true
                    }
                }

                if (changed) {
                    changeCount++

                    forEach { q2 -> if (q2.reach < q.reach) q2.reach = q.reach }
                }
            }

            if (changeCount == 0) break
            iter++
        }

        if (false) println(joinToString("\n") { q -> "${q.which},${q.reach},${round(q.reach * 100.0 / size.toDouble())}" })
    }

    fun <T: JukeboxSegmentedType<T>> Array<T>.findBestLastBeat(): Int {
        val reachThreshold = 50 //TODO: Move this up to jukeboxData?
        var longest = 0
        var longestReach = 0.0
        for (i in indices.reversed()) {
            val q = this[i]
            //val reach = q.reach * 100.0 / size.toDouble()
            val distanceToEnd = size - i
            //sic. Paul Lamere
            // if q is the last quanta, then we can never go past it
            // which limits our reach
            val reach = (q.reach - distanceToEnd) * 100.0 / size.toDouble()
            if (reach > longestReach && q.neighbours.isNotEmpty()) {
                longestReach = reach
                longest = i
                if (reach >= reachThreshold) break
            }
        }

        //jukeboxData.totalBeats = quanta.length
        //jukeboxData.longestReach = longestReach
        return longest
    }

    fun <T: JukeboxSegmentedType<T>> Array<T>.filterOutBadBranches(lastIndex: Int) =
            forEach { q -> q.neighbours.removeAll { neighbour -> neighbour.dest.which >= lastIndex } }

    fun <T: JukeboxSegmentedType<T>> Array<T>.filterOutSequentialBranches(lastBranchPoint: Int) =
            forEach { q -> q.neighbours.removeAll { neighbour -> hasSequentialBranch(q, neighbour, lastBranchPoint) } }

    fun <T: JukeboxSegmentedType<T>> hasSequentialBranch(q: T, neighbour: JukeboxEdge<T>, lastBranchPoint: Int): Boolean {
        if (q.which == lastBranchPoint) return false

        val qPrevious = q.prev
        if (qPrevious != null) {
            val distance = q.which - neighbour.dest.which
            return qPrevious.neighbours.any { qNeighbour -> distance == qPrevious.which - qNeighbour.dest.which }
        }

        return false
    }
}