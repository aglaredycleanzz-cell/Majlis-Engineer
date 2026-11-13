package com.majlisengineer.cad

import com.majlisengineer.data.CadDrawingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CadPreviewTest {
    private fun entity(id: String, x: Double) = CadDrawingEntity(
        id = id, projectId = "p", layerId = "l", type = "LINE", x = x, y = 0.0,
        width = 1.0, height = 1.0
    )

    @Test fun newPreviewIsAddedToRenderedItems() {
        val stored = listOf(entity("stored", 0.0))
        val rendered = mergeCadPreview(stored, entity("preview", 2.0))
        assertEquals(listOf("stored", "preview"), rendered.map { it.id })
    }

    @Test fun movingExistingEntityReplacesItWithoutDuplicating() {
        val stored = listOf(entity("one", 0.0), entity("two", 4.0))
        val rendered = mergeCadPreview(stored, entity("one", 3.0))
        assertEquals(listOf("one", "two"), rendered.map { it.id })
        assertEquals(3.0, rendered.first().x, 0.0)
    }
}
