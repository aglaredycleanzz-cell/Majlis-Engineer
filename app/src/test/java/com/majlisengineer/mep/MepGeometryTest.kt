package com.majlisengineer.mep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MepGeometryTest {
    @Test
    fun codecRoundTripsDecimalCoordinatesIndependentOfLocale() {
        val points = listOf(MepPoint(0.125, 1.5, 2.75), MepPoint(3.0, 1.5, 2.75))
        val encoded = MepGeometryCodec.encode(points)
        assertNotNull(encoded)
        assertEquals(points, MepGeometryCodec.decode(encoded!!))
    }

    @Test
    fun straightRunLengthUsesThreeDimensionalMetres() {
        val points = listOf(MepPoint(0.0, 0.0, 0.0), MepPoint(3.0, 4.0, 0.0))
        assertEquals(5.0, MepGeometryCodec.lengthMeters(MepElementType.PIPE_RUN, points), 1e-9)
    }

    @Test
    fun localYRotationAlignsVerticalAndHorizontalSegments() {
        val vertical = MepGeometryCodec.localYToSegmentRotation(MepPoint(0.0, 0.0, 0.0), MepPoint(0.0, 2.0, 0.0))!!
        assertEquals(0.0, vertical.xDegrees, 1e-9)
        assertEquals(0.0, vertical.zDegrees, 1e-9)

        val alongX = MepGeometryCodec.localYToSegmentRotation(MepPoint(0.0, 0.0, 0.0), MepPoint(2.0, 0.0, 0.0))!!
        assertEquals(0.0, alongX.xDegrees, 1e-9)
        assertEquals(-90.0, alongX.zDegrees, 1e-9)

        val alongZ = MepGeometryCodec.localYToSegmentRotation(MepPoint(0.0, 0.0, 0.0), MepPoint(0.0, 0.0, 2.0))!!
        assertEquals(90.0, alongZ.xDegrees, 1e-9)
        assertEquals(0.0, alongZ.zDegrees, 1e-9)
    }

    @Test
    fun decimalParserAcceptsArabicAndPersianNumerals() {
        assertEquals(12.75, MepGeometryCodec.parseDecimal("١٢٫٧٥")!!, 1e-9)
        assertEquals(3.5, MepGeometryCodec.parseDecimal("۳,۵")!!, 1e-9)
        assertEquals(-1.25, MepGeometryCodec.parseDecimal("−١٫٢٥")!!, 1e-9)
        assertEquals(null, MepGeometryCodec.parseDecimal("غير رقم"))
    }

    @Test
    fun teeLengthCountsItsThreeBranchesAndRequiresFourPoints() {
        val points = listOf(
            MepPoint(1.0, 1.0, 1.0), MepPoint(0.8, 1.0, 1.0),
            MepPoint(1.2, 1.0, 1.0), MepPoint(1.0, 1.0, 1.2)
        )
        assertNull(MepGeometryCodec.validate(MepElementType.TEE, points))
        assertEquals(0.6, MepGeometryCodec.lengthMeters(MepElementType.TEE, points), 1e-9)
        assertNotNull(MepGeometryCodec.validate(MepElementType.TEE, points.take(3)))
    }

    @Test
    fun rejectsNonFiniteCoordinatesAndOutOfRoomPoints() {
        assertNull(MepGeometryCodec.encode(listOf(MepPoint(Double.NaN, 0.0, 0.0))))
        val bounds = MepRoomBounds(5.0, 4.0, 3.0)
        assertTrue(bounds.contains(MepPoint(5.0, 3.0, 4.0)))
        assertTrue(!bounds.contains(MepPoint(5.1, 3.0, 4.0)))
    }

    @Test
    fun rejectsInvalidRoomDimensionsBeforeAcceptingPoints() {
        val zeroWidth = MepRoomBounds(5.0, 0.0, 3.0)
        assertTrue(!zeroWidth.isValid)
        assertTrue(!zeroWidth.contains(MepPoint(1.0, 1.0, 0.0)))

        val nonFiniteHeight = MepRoomBounds(5.0, 4.0, Double.NaN)
        assertTrue(!nonFiniteHeight.isValid)
        assertTrue(!nonFiniteHeight.contains(MepPoint(1.0, 1.0, 1.0)))
    }

    @Test
    fun elbowIsTwoMeasuredLegsAndKeepsItsBendPoint() {
        val points = listOf(MepPoint(0.0, 1.0, 0.0), MepPoint(0.0, 1.0, 2.0), MepPoint(3.0, 1.0, 2.0))
        assertNull(MepGeometryCodec.validate(MepElementType.ELBOW, points))
        assertEquals(5.0, MepGeometryCodec.lengthMeters(MepElementType.ELBOW, points), 1e-9)
        assertEquals(points[1], MepGeometryCodec.fittingAnchor(MepElementType.ELBOW, points))
    }
}
