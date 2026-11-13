package com.majlisengineer.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteriorDesignModelsTest {
    @Test
    fun calculatesRoomAreasAndOpenings() {
        val room = InteriorRoom(lengthM = 8.0, widthM = 5.0, heightM = 3.0, doorWidthM = 1.0, windowCount = 2)
        assertEquals(40.0, room.floorAreaM2, 0.0001)
        assertEquals(78.0, room.grossWallAreaM2, 0.0001)
        assertEquals(73.4, room.netWallAreaM2(), 0.0001)
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertTrue(!InteriorRoom(lengthM = -2.0).isValid)
        assertTrue(!InteriorRoom(heightM = 1.5).isValid)
    }

    @Test
    fun proposedSeatingStaysInsideRoom() {
        val room = InteriorRoom()
        val seating = MajlisLayoutPlanner.perimeterSeating(room)
        assertTrue(seating.isNotEmpty())
        assertTrue(seating.all { it.isInside(room) })
    }

    @Test
    fun seatingPlannerHonorsTheSelectedLayout() {
        val room = InteriorRoom()
        assertEquals(4, MajlisLayoutPlanner.seating(room, SeatingLayout.PERIMETER).size)
        assertEquals(3, MajlisLayoutPlanner.seating(room, SeatingLayout.U_SHAPE).size)
        assertEquals(2, MajlisLayoutPlanner.seating(room, SeatingLayout.L_SHAPE).size)
        assertEquals(2, MajlisLayoutPlanner.seating(room, SeatingLayout.MIXED).size)
        assertTrue(SeatingLayout.values().flatMap { MajlisLayoutPlanner.seating(room, it) }.all { it.isInside(room) })
    }
}
