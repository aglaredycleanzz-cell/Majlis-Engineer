package com.majlisengineer.mep

import kotlin.math.sqrt
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.round

/** Local project coordinates in metres: X=room length, Y=height above floor, Z=room width. */
data class MepPoint(val x: Double, val y: Double, val z: Double)

data class MepEulerRotation(val xDegrees: Double, val yDegrees: Double, val zDegrees: Double)

enum class MepSystem(
    val label: String,
    val discipline: MepDiscipline,
    val colorHex: String
) {
    COLD_WATER("مياه باردة", MepDiscipline.PLUMBING, "#1687C9"),
    HOT_WATER("مياه ساخنة", MepDiscipline.PLUMBING, "#D84A3A"),
    DRAINAGE("صرف صحي", MepDiscipline.PLUMBING, "#68747D"),
    PHASE_R("كهرباء — طور R", MepDiscipline.ELECTRICAL, "#D32F2F"),
    PHASE_Y("كهرباء — طور Y", MepDiscipline.ELECTRICAL, "#F9A825"),
    PHASE_B("كهرباء — طور B", MepDiscipline.ELECTRICAL, "#1565C0"),
    NEUTRAL("كهرباء — محايد", MepDiscipline.ELECTRICAL, "#202124"),
    EARTH("كهرباء — حماية أرضية", MepDiscipline.ELECTRICAL, "#66A61E");

    companion object {
        fun fromStored(value: String): MepSystem? = entries.firstOrNull { it.name == value }
    }
}

enum class MepDiscipline { PLUMBING, ELECTRICAL }

enum class MepElementType(val label: String) {
    PIPE_RUN("مسار أنبوب"),
    CONDUIT_RUN("مسار ماسورة كهرباء"),
    CABLE_RUN("مسار كابل"),
    ELBOW("كوع"),
    TEE("وصلة T"),
    VALVE("محبس"),
    LIGHT_POINT("نقطة إنارة"),
    SWITCH_POINT("مفتاح"),
    SOCKET_POINT("مقبس كهرباء");

    val isRun: Boolean get() = this == PIPE_RUN || this == CONDUIT_RUN || this == CABLE_RUN
    val isFitting: Boolean get() = this == ELBOW || this == TEE || this == VALVE

    companion object {
        fun fromStored(value: String): MepElementType? = entries.firstOrNull { it.name == value }

        fun allowedFor(system: MepSystem): List<MepElementType> = when (system.discipline) {
            MepDiscipline.PLUMBING -> listOf(PIPE_RUN, ELBOW, TEE, VALVE)
            MepDiscipline.ELECTRICAL -> listOf(CONDUIT_RUN, CABLE_RUN, ELBOW, TEE, LIGHT_POINT, SWITCH_POINT, SOCKET_POINT)
        }
    }
}

data class MepRoomBounds(val lengthM: Double, val widthM: Double, val heightM: Double) {
    val isValid: Boolean get() = listOf(lengthM, widthM, heightM).all { it.isFinite() && it > 0.0 && it <= 1000.0 }

    fun contains(point: MepPoint, tolerance: Double = 0.001): Boolean =
        isValid && point.x >= -tolerance && point.x <= lengthM + tolerance &&
            point.y >= -tolerance && point.y <= heightM + tolerance &&
            point.z >= -tolerance && point.z <= widthM + tolerance
}

/** Stable, locale-independent point encoding for Room. It stores geometry, not a flattened image. */
object MepGeometryCodec {
    private const val MAX_POINTS = 16
    private const val MAX_COORDINATE_METRES = 1000.0

    fun parseDecimal(input: String): Double? {
        val normalized = buildString {
            input.trim().forEach { character ->
                when (character) {
                    in '\u0660'..'\u0669' -> append(('0'.code + character.code - '\u0660'.code).toChar())
                    in '\u06f0'..'\u06f9' -> append(('0'.code + character.code - '\u06f0'.code).toChar())
                    '\u066b', ',' -> append('.')
                    '\u066c' -> Unit
                    '\u2212' -> append('-')
                    else -> append(character)
                }
            }
        }
        return normalized.toDoubleOrNull()
    }

    fun snapToRoom(point: MepPoint, bounds: MepRoomBounds, gridM: Double = 0.05): MepPoint? {
        if (!bounds.isValid || !gridM.isFinite() || gridM <= 0.0 || listOf(point.x, point.y, point.z).any { !it.isFinite() }) return null
        fun snap(value: Double, maximum: Double) = (round(value / gridM) * gridM).coerceIn(0.0, maximum)
        return MepPoint(snap(point.x, bounds.lengthM), snap(point.y, bounds.heightM), snap(point.z, bounds.widthM))
    }

    fun encode(points: List<MepPoint>): String? {
        if (points.isEmpty() || points.size > MAX_POINTS || points.any(::isInvalid)) return null
        return points.joinToString(";") { "${it.x},${it.y},${it.z}" }
    }

    fun decode(encoded: String): List<MepPoint>? {
        if (encoded.isBlank() || encoded.length > 4096) return null
        val tokens = encoded.split(';')
        if (tokens.isEmpty() || tokens.size > MAX_POINTS) return null
        val points = tokens.map { token ->
            val coordinates = token.split(',')
            if (coordinates.size != 3) return null
            val values = coordinates.map { it.toDoubleOrNull() ?: return null }
            MepPoint(values[0], values[1], values[2])
        }
        return points.takeIf { it.none(::isInvalid) }
    }

    fun validate(type: MepElementType, points: List<MepPoint>): String? {
        if (points.any(::isInvalid)) return "الإحداثيات غير صالحة أو خارج الحدود الآمنة."
        val expected = when (type) {
            MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN -> 2..MAX_POINTS
            MepElementType.ELBOW -> 3..3
            MepElementType.TEE -> 4..4
            MepElementType.VALVE, MepElementType.LIGHT_POINT, MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> 1..1
        }
        if (points.size !in expected) return when (type) {
            MepElementType.ELBOW -> "الكوع يحتاج نقطة بداية ونقطة انحناء ونقطة نهاية."
            MepElementType.TEE -> "وصلة T تحتاج نقطة مركز وثلاثة أطراف."
            MepElementType.VALVE, MepElementType.LIGHT_POINT, MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> "هذا العنصر يحتاج نقطة واحدة."
            else -> "المسار يحتاج نقطتين على الأقل."
        }
        if (points.zipWithNext().any { (a, b) -> distance(a, b) < 0.001 }) return "يوجد مقطع بطول صفري."
        if (type == MepElementType.TEE && points.drop(1).any { distance(points.first(), it) < 0.001 }) {
            return "أطراف وصلة T يجب أن تختلف عن المركز."
        }
        return null
    }

    fun lengthMeters(type: MepElementType, points: List<MepPoint>): Double = when (type) {
        MepElementType.TEE -> points.drop(1).sumOf { distance(points.first(), it) }
        MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN, MepElementType.ELBOW ->
            points.zipWithNext().sumOf { (a, b) -> distance(a, b) }
        MepElementType.VALVE, MepElementType.LIGHT_POINT, MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> 0.0
    }

    fun fittingAnchor(type: MepElementType, points: List<MepPoint>): MepPoint? = when (type) {
        MepElementType.ELBOW -> points.getOrNull(1)
        MepElementType.TEE, MepElementType.VALVE, MepElementType.LIGHT_POINT,
        MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> points.firstOrNull()
        else -> null
    }

    fun defaultFittingPoints(type: MepElementType, center: MepPoint, armM: Double = 0.18): List<MepPoint> = when (type) {
        MepElementType.ELBOW -> listOf(
            center.copy(x = center.x - armM), center, center.copy(z = center.z + armM)
        )
        MepElementType.TEE -> listOf(
            center, center.copy(x = center.x - armM), center.copy(x = center.x + armM), center.copy(z = center.z + armM)
        )
        else -> listOf(center)
    }

    fun distance(a: MepPoint, b: MepPoint): Double = sqrt(
        (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y) + (b.z - a.z) * (b.z - a.z)
    )

    /** Euler degrees in ZYX order to align a cylinder's local +Y axis with a segment. */
    fun localYToSegmentRotation(start: MepPoint, end: MepPoint): MepEulerRotation? {
        val distance = distance(start, end)
        if (!distance.isFinite() || distance < 1e-9) return null
        val nx = (end.x - start.x) / distance
        val ny = (end.y - start.y) / distance
        val nz = (end.z - start.z) / distance
        return MepEulerRotation(
            xDegrees = Math.toDegrees(asin(nz.coerceIn(-1.0, 1.0))),
            yDegrees = 0.0,
            zDegrees = Math.toDegrees(atan2(-nx, ny))
        )
    }

    private fun isInvalid(point: MepPoint): Boolean =
        !point.x.isFinite() || !point.y.isFinite() || !point.z.isFinite() ||
            kotlin.math.abs(point.x) > MAX_COORDINATE_METRES ||
            kotlin.math.abs(point.y) > MAX_COORDINATE_METRES ||
            kotlin.math.abs(point.z) > MAX_COORDINATE_METRES
}
