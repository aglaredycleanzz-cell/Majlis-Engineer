package com.majlisengineer.design

import kotlin.math.sqrt

enum class MepDiscipline(val label: String) {
    WATER("تغذية المياه"),
    DRAIN("الصرف الصحي"),
    ELECTRICAL("الكهرباء")
}

data class MepPoint3D(
    val x: Double,
    val y: Double,
    val z: Double
) {
    fun isValid(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() &&
        x >= 0.0 && y >= 0.0 && z >= 0.0
}

data class MepRouteSegment(
    val id: String,
    val discipline: MepDiscipline,
    val start: MepPoint3D,
    val end: MepPoint3D,
    val nominalDiameterMm: Double = 20.0,
    val note: String = ""
) {
    val lengthMeters: Double
        get() {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val dz = end.z - start.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

    fun isValid(roomLength: Double, roomWidth: Double, roomHeight: Double): Boolean =
        roomLength > 0.0 && roomWidth > 0.0 && roomHeight > 0.0 &&
            start.isValid() && end.isValid() &&
            start.x <= roomLength && end.x <= roomLength &&
            start.y <= roomWidth && end.y <= roomWidth &&
            start.z <= roomHeight && end.z <= roomHeight &&
            nominalDiameterMm.isFinite() && nominalDiameterMm > 0.0
}

data class MepQuantitySummary(
    val waterMeters: Double,
    val drainMeters: Double,
    val electricalMeters: Double,
    val segmentCount: Int
)

object MepQuantityCalculator {
    fun summarize(segments: List<MepRouteSegment>): MepQuantitySummary =
        MepQuantitySummary(
            waterMeters = segments.filter { it.discipline == MepDiscipline.WATER }.sumOf { it.lengthMeters },
            drainMeters = segments.filter { it.discipline == MepDiscipline.DRAIN }.sumOf { it.lengthMeters },
            electricalMeters = segments.filter { it.discipline == MepDiscipline.ELECTRICAL }.sumOf { it.lengthMeters },
            segmentCount = segments.size
        )
}
