package com.majlisengineer.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MepClashDetectorTest {
    private fun segment(
        id: String,
        discipline: MepDiscipline,
        start: MepPoint3D,
        end: MepPoint3D,
        diameterMm: Double = 20.0
    ) = MepRouteSegment(id, discipline, start, end, diameterMm)

    @Test
    fun detectsTrueThreeDimensionalIntersectionAsHardClash() {
        val water = segment("water-1", MepDiscipline.WATER,
            MepPoint3D(0.0, 0.0, 1.0), MepPoint3D(2.0, 0.0, 1.0))
        val electrical = segment("elec-1", MepDiscipline.ELECTRICAL,
            MepPoint3D(1.0, -1.0, 1.0), MepPoint3D(1.0, 1.0, 1.0))

        val clashes = MepClashDetector.detect(listOf(water, electrical))
        assertEquals(1, clashes.size)
        assertEquals(MepClashSeverity.HARD_CLASH, clashes.single().severity)
    }

    @Test
    fun ignoresIntersectionInPlanWhenRoutesAreSeparatedByHeight() {
        val water = segment("water-1", MepDiscipline.WATER,
            MepPoint3D(0.0, 0.0, 0.5), MepPoint3D(2.0, 0.0, 0.5))
        val electrical = segment("elec-1", MepDiscipline.ELECTRICAL,
            MepPoint3D(1.0, -1.0, 1.5), MepPoint3D(1.0, 1.0, 1.5))

        assertTrue(MepClashDetector.detect(listOf(water, electrical)).isEmpty())
    }

    @Test
    fun reportsInsufficientClearanceForNearbyParallelRoutes() {
        val water = segment("water-1", MepDiscipline.WATER,
            MepPoint3D(0.0, 0.0, 1.0), MepPoint3D(2.0, 0.0, 1.0))
        val electrical = segment("elec-1", MepDiscipline.ELECTRICAL,
            MepPoint3D(0.0, 0.06, 1.0), MepPoint3D(2.0, 0.06, 1.0))

        val clashes = MepClashDetector.detect(listOf(water, electrical), requiredClearanceMm = 50.0)
        assertEquals(1, clashes.size)
        assertEquals(MepClashSeverity.CLEARANCE_WARNING, clashes.single().severity)
    }

    @Test
    fun doesNotReportRoutesWithSufficientClearance() {
        val water = segment("water-1", MepDiscipline.WATER,
            MepPoint3D(0.0, 0.0, 1.0), MepPoint3D(2.0, 0.0, 1.0))
        val electrical = segment("elec-1", MepDiscipline.ELECTRICAL,
            MepPoint3D(0.0, 0.2, 1.0), MepPoint3D(2.0, 0.2, 1.0))

        assertTrue(MepClashDetector.detect(listOf(water, electrical), requiredClearanceMm = 50.0).isEmpty())
    }
}
