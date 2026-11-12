package com.majlisengineer.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MepEngineeringModelsTest {
    @Test
    fun calculatesThreeDimensionalSegmentLength() {
        val segment = MepRouteSegment(
            id = "s1",
            discipline = MepDiscipline.WATER,
            start = MepPoint3D(0.0, 0.0, 0.0),
            end = MepPoint3D(3.0, 4.0, 0.0)
        )
        assertEquals(5.0, segment.lengthMeters, 0.0001)
    }

    @Test
    fun rejectsRouteOutsideRoomBounds() {
        val segment = MepRouteSegment(
            id = "s2",
            discipline = MepDiscipline.ELECTRICAL,
            start = MepPoint3D(0.0, 0.0, 0.0),
            end = MepPoint3D(4.0, 2.0, 2.5)
        )
        assertFalse(segment.isValid(roomLength = 3.0, roomWidth = 4.0, roomHeight = 3.0))
    }

    @Test
    fun summarizesQuantitiesByDiscipline() {
        val segments = listOf(
            MepRouteSegment("1", MepDiscipline.WATER, MepPoint3D(0.0, 0.0, 0.0), MepPoint3D(3.0, 0.0, 0.0)),
            MepRouteSegment("2", MepDiscipline.DRAIN, MepPoint3D(0.0, 0.0, 0.0), MepPoint3D(0.0, 4.0, 0.0)),
            MepRouteSegment("3", MepDiscipline.ELECTRICAL, MepPoint3D(0.0, 0.0, 0.0), MepPoint3D(0.0, 0.0, 2.0))
        )
        val summary = MepQuantityCalculator.summarize(segments)
        assertEquals(3.0, summary.waterMeters, 0.0001)
        assertEquals(4.0, summary.drainMeters, 0.0001)
        assertEquals(2.0, summary.electricalMeters, 0.0001)
        assertEquals(3, summary.segmentCount)
        assertTrue(segments.all { it.isValid(5.0, 5.0, 3.0) })
    }
}
