package com.majlisengineer.proof.cad

import org.junit.Assert.*
import org.junit.Test

class ProofDrawingModelTest {
    @Test fun createsDrawingAndAddsGeometryToLayer() {
        val model = ProofDrawingModel()
        assertTrue(model.createLayer("arch", "معماري"))
        assertTrue(model.addEntity(ProofEntity("line-1", "arch", "line", 1.0, 2.0, 4.0, 0.0)))
        assertEquals(1, model.entities.size)
    }

    @Test fun movesAndEditsEntity() {
        val model = fixture()
        assertTrue(model.moveEntity("line-1", 3.0, -1.0))
        assertTrue(model.editEntity("line-1", "جدار", 8.0, 0.0))
        val entity = model.entities.single()
        assertEquals(4.0, entity.x, 0.0)
        assertEquals(1.0, entity.y, 0.0)
        assertEquals("جدار", entity.label)
        assertEquals(8.0, entity.width, 0.0)
    }

    @Test fun rejectsMovesThatOverflowCoordinates() {
        val model = fixture()
        assertFalse(model.moveEntity("line-1", Double.MAX_VALUE, 0.0))
        val entity = model.entities.single()
        assertEquals(1.0, entity.x, 0.0)
        assertEquals(2.0, entity.y, 0.0)
    }

    @Test fun rejectsNegativeDimensionsAndInvalidEntities() {
        val model = ProofDrawingModel()
        assertFalse(model.addEntity(ProofEntity("x", "missing", "line", 0.0, 0.0)))
        assertTrue(model.createLayer("arch", "معماري"))
        assertFalse(model.addEntity(ProofEntity("nan", "arch", "line", Double.NaN, 0.0)))
        assertFalse(model.addEntity(ProofEntity("negative", "arch", "RECT", 0.0, 0.0, -1.0, 2.0)))
        assertTrue(model.addEntity(ProofEntity("valid", "arch", "LINE", 0.0, 0.0, 1.0, 0.0)))
        assertFalse(model.editEntity("valid", "غير صالح", -1.0, 1.0))
    }

    @Test fun deletesEntityAndTogglesLayerVisibility() {
        val model = fixture()
        assertTrue(model.setLayerVisibility("arch", false))
        assertTrue(model.visibleEntities().isEmpty())
        assertTrue(model.deleteEntity("line-1"))
        assertFalse(model.deleteEntity("line-1"))
    }

    @Test fun snapshotCanBeReopenedWithoutLosingEntities() {
        val model = fixture()
        val saved = model.snapshot()
        val reopened = ProofDrawingModel()
        reopened.reopen(saved)
        assertEquals(model.layers, reopened.layers)
        assertEquals(model.entities, reopened.entities)
    }

    @Test fun handlesRepresentativeDrawingSizesIndependently() {
        listOf(50, 500, 2000).forEach { count ->
            val model = ProofDrawingModel()
            assertTrue(model.createLayer("arch", "معماري"))
            repeat(count) { index ->
                assertTrue(
                    "Could not add entity $index for drawing size $count",
                    model.addEntity(ProofEntity("e-$index", "arch", "RECT", index.toDouble(), 0.0, 1.0, 1.0))
                )
            }
            assertEquals("Unexpected entity count for drawing size $count", count, model.entities.size)
            val restored = ProofDrawingModel().apply { reopen(model.snapshot()) }
            assertEquals("Snapshot restore failed for drawing size $count", count, restored.entities.size)
        }
    }

    private fun fixture() = ProofDrawingModel().apply {
        createLayer("arch", "معماري")
        addEntity(ProofEntity("line-1", "arch", "line", 1.0, 2.0, 4.0, 0.0))
    }
}
