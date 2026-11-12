package com.majlisengineer.design

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class MepClashSeverity { HARD_CLASH, CLEARANCE_WARNING }

data class MepClash(
    val first: MepRouteSegment,
    val second: MepRouteSegment,
    val surfaceGapMeters: Double,
    val severity: MepClashSeverity
)

object MepClashDetector {
    /**
     * Compares only different disciplines. Coordinates are metres; nominal diameters
     * are treated as approximate physical diameters, not verified outside diameters.
     */
    fun detect(
        segments: List<MepRouteSegment>,
        requiredClearanceMm: Double = 50.0
    ): List<MepClash> {
        require(requiredClearanceMm.isFinite() && requiredClearanceMm >= 0.0)
        val results = mutableListOf<MepClash>()
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                val a = segments[i]
                val b = segments[j]
                if (a.discipline == b.discipline) continue
                if (!a.nominalDiameterMm.isFinite() || !b.nominalDiameterMm.isFinite() ||
                    a.nominalDiameterMm <= 0.0 || b.nominalDiameterMm <= 0.0) continue

                val centerDistance = segmentDistance(a.start, a.end, b.start, b.end)
                val radiiMeters = (a.nominalDiameterMm + b.nominalDiameterMm) / 2000.0
                val gap = centerDistance - radiiMeters
                val clearanceMeters = requiredClearanceMm / 1000.0
                if (gap < 0.0) {
                    results += MepClash(a, b, gap, MepClashSeverity.HARD_CLASH)
                } else if (gap < clearanceMeters) {
                    results += MepClash(a, b, gap, MepClashSeverity.CLEARANCE_WARNING)
                }
            }
        }
        return results.sortedBy { it.surfaceGapMeters }
    }

    /** Minimum Euclidean distance between two finite 3D line segments. */
    internal fun segmentDistance(p1: MepPoint3D, q1: MepPoint3D, p2: MepPoint3D, q2: MepPoint3D): Double {
        val d1 = vector(p1, q1)
        val d2 = vector(p2, q2)
        val r = vector(p2, p1)
        val a = dot(d1, d1)
        val e = dot(d2, d2)
        val f = dot(d2, r)
        val epsilon = 1e-12

        var s: Double
        var t: Double
        if (a <= epsilon && e <= epsilon) return distance(p1, p2)
        if (a <= epsilon) {
            s = 0.0
            t = (f / e).coerceIn(0.0, 1.0)
        } else {
            val c = dot(d1, r)
            if (e <= epsilon) {
                t = 0.0
                s = (-c / a).coerceIn(0.0, 1.0)
            } else {
                val b = dot(d1, d2)
                val denominator = a * e - b * b
                s = if (denominator != 0.0) ((b * f - c * e) / denominator).coerceIn(0.0, 1.0) else 0.0
                t = (b * s + f) / e
                if (t < 0.0) {
                    t = 0.0
                    s = (-c / a).coerceIn(0.0, 1.0)
                } else if (t > 1.0) {
                    t = 1.0
                    s = ((b - c) / a).coerceIn(0.0, 1.0)
                }
            }
        }
        val closest = MepPoint3D(
            p1.x + d1[0] * s - p2.x - d2[0] * t,
            p1.y + d1[1] * s - p2.y - d2[1] * t,
            p1.z + d1[2] * s - p2.z - d2[2] * t
        )
        return sqrt(dot(doubleArrayOf(closest.x, closest.y, closest.z), doubleArrayOf(closest.x, closest.y, closest.z)))
    }

    private fun vector(from: MepPoint3D, to: MepPoint3D) =
        doubleArrayOf(to.x - from.x, to.y - from.y, to.z - from.z)

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun distance(a: MepPoint3D, b: MepPoint3D): Double {
        val v = vector(a, b)
        return sqrt(dot(v, v))
    }
}
