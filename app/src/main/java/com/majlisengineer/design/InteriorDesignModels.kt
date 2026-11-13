package com.majlisengineer.design

import kotlin.math.PI
import kotlin.math.max

/**
 * Interior-design domain model for an Omani majlis.
 * All dimensions are metres. This model is deliberately independent from Room
 * and the existing CAD persistence schema.
 */
enum class MajlisStyle(val title: String) {
    OMANI_TRADITIONAL("عُماني تقليدي"),
    MODERN("عصري"),
    CONTEMPORARY("معاصر"),
    CLASSIC("كلاسيكي")
}

enum class SeatingLayout(val title: String) {
    PERIMETER("جلسة محيطية"),
    U_SHAPE("جلسة حرف U"),
    L_SHAPE("جلسة حرف L"),
    MIXED("جلسة مختلطة")
}

enum class FinishPalette(val title: String, val wallHex: String, val floorHex: String, val accentHex: String) {
    SAND("رملي دافئ", "#E7D8BF", "#C7B69B", "#8A6747"),
    WHITE_WOOD("أبيض وخشب", "#F2EFE8", "#B58B62", "#5C4636"),
    STONE("حجر طبيعي", "#D5D0C7", "#89857D", "#494741"),
    GREEN_GOLD("أخضر وذهبي", "#E8E1D2", "#B7A58A", "#315C4C")
}

data class InteriorRoom(
    val lengthM: Double = 8.0,
    val widthM: Double = 5.0,
    val heightM: Double = 3.2,
    val doorWidthM: Double = 1.0,
    val windowCount: Int = 2,
    val style: MajlisStyle = MajlisStyle.OMANI_TRADITIONAL,
    val seating: SeatingLayout = SeatingLayout.PERIMETER,
    val palette: FinishPalette = FinishPalette.SAND
) {
    val isValid: Boolean
        get() = listOf(lengthM, widthM, heightM, doorWidthM).all { it.isFinite() } &&
            lengthM in 2.0..30.0 && widthM in 2.0..30.0 && heightM in 2.2..8.0 &&
            doorWidthM in 0.6..2.5 && doorWidthM < minOf(lengthM, widthM) &&
            windowCount in 0..12

    val floorAreaM2: Double get() = lengthM * widthM
    val perimeterM: Double get() = 2.0 * (lengthM + widthM)
    val grossWallAreaM2: Double get() = perimeterM * heightM

    fun netWallAreaM2(windowAreaEachM2: Double = 1.2, doorHeightM: Double = 2.2): Double {
        require(windowAreaEachM2 >= 0.0 && doorHeightM >= 0.0)
        val openings = doorWidthM * doorHeightM + windowCount * windowAreaEachM2
        return max(0.0, grossWallAreaM2 - openings)
    }

    fun validate(): InteriorRoom {
        require(isValid) { "أبعاد المجلس أو عدد النوافذ خارج النطاق المسموح." }
        return this
    }
}

data class InteriorElement(
    val id: String,
    val kind: String,
    val xM: Double,
    val yM: Double,
    val widthM: Double,
    val depthM: Double,
    val rotationDegrees: Int = 0,
    val label: String = ""
) {
    fun isInside(room: InteriorRoom): Boolean =
        xM.isFinite() && yM.isFinite() && widthM.isFinite() && depthM.isFinite() &&
            widthM > 0.0 && depthM > 0.0 &&
            xM >= 0.0 && yM >= 0.0 &&
            xM + widthM <= room.lengthM && yM + depthM <= room.widthM
}

object MajlisLayoutPlanner {
    /**
     * Creates a simple, editable perimeter seating concept.
     * The output is a 2D placement proposal, not a construction drawing.
     */
    fun perimeterSeating(room: InteriorRoom, seatDepthM: Double = 0.85): List<InteriorElement> {
        return seating(room, SeatingLayout.PERIMETER, seatDepthM)
    }

    fun seating(room: InteriorRoom, layout: SeatingLayout, seatDepthM: Double = 0.82): List<InteriorElement> {
        room.validate()
        require(seatDepthM in 0.5..1.3)
        val gap = 0.08
        val horizontalRun = max(0.0, room.lengthM - 2 * seatDepthM - 2 * gap)
        val verticalRun = max(0.0, room.widthM - 2 * seatDepthM - 2 * gap)
        val top = if (horizontalRun > 0.5) InteriorElement("seat-top", "SEATING", seatDepthM + gap, gap,
            horizontalRun, seatDepthM, label = "جلسة خلفية") else null
        val bottom = if (horizontalRun > 0.5) InteriorElement("seat-bottom", "SEATING", seatDepthM + gap,
            room.widthM - seatDepthM - gap, horizontalRun, seatDepthM, label = "جلسة أمامية") else null
        val left = if (verticalRun > 0.5) InteriorElement("seat-left", "SEATING", gap, seatDepthM + gap,
            seatDepthM, verticalRun, label = "جلسة جانبية") else null
        val right = if (verticalRun > 0.5) InteriorElement("seat-right", "SEATING", room.lengthM - seatDepthM - gap,
            seatDepthM + gap, seatDepthM, verticalRun, label = "جلسة جانبية") else null
        val elements = when (layout) {
            SeatingLayout.PERIMETER -> listOfNotNull(top, bottom, left, right)
            SeatingLayout.U_SHAPE -> listOfNotNull(top, left, right)
            SeatingLayout.L_SHAPE -> listOfNotNull(top, left)
            SeatingLayout.MIXED -> listOfNotNull(top, bottom)
        }
        return elements.filter { it.isInside(room) }
    }
}
