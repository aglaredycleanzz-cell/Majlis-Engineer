package com.majlisengineer.cad.dxf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DxfWriterTest {
    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/dxf/$name")!!.readBytes().toString(Charsets.UTF_8)

    private val sample = DxfDocument(
        unitsCode = DxfUnits.METERS.code,
        layers = listOf("Architectural", "Electrical"),
        entities = listOf(
            DxfLine("Architectural", DxfPoint(-1.5, 0.0), DxfPoint(6.25, 4.125)),
            DxfPolyline("Architectural", listOf(DxfVertex(0.0, 0.0), DxfVertex(6.0, 0.0, 0.5), DxfVertex(6.0, 4.0)), closed = true),
            DxfPolyline("Architectural", listOf(DxfVertex(0.0, 0.0), DxfVertex(1.0, 1.0)), closed = false),
            DxfCircle("Electrical", DxfPoint(3.0, 2.0), 0.35),
            DxfArc("Architectural", DxfPoint(1.0, 1.0), 0.9, 30.0, 120.0),
            DxfText("Architectural", DxfPoint(2.0, 3.0), 0.25, "مجلس رجال – صالة 1", 15.0),
            DxfText("Electrical", DxfPoint(4.0, 3.0), 0.2, "Majlis A")
        )
    )

    @Test fun roundTripsEverySupportedEntityExactly() {
        val back = DxfReader.read(DxfWriter.write(sample))
        assertTrue(back.unsupportedEntityCounts.isEmpty())
        assertEquals(sample.unitsCode, back.document.unitsCode)
        assertEquals(sample.entities, back.document.entities)
        assertTrue(back.document.layers.containsAll(sample.layers))
    }

    @Test fun outputIsDeterministicAndStableAcrossReadWriteCycles() {
        val first = DxfWriter.write(sample)
        assertEquals(first, DxfWriter.write(sample))
        assertEquals(first, DxfWriter.write(DxfReader.read(first).document))
    }

    @Test fun supportedContentOfExternalFileSurvivesReadWriteRead() {
        val original = DxfReader.read(fixture("basic_r2000_meters.dxf")).document
        val again = DxfReader.read(DxfWriter.write(original)).document
        assertEquals(original, again)
    }

    @Test fun exportOfPartiallyImportedFileHoldsOnlySupportedEntitiesAndImportReportTellsWhatWasLeftOut() {
        val imported = DxfReader.read(fixture("unsupported_mix.dxf"))
        assertTrue(imported.unsupportedEntityCounts.isNotEmpty())
        val exported = DxfReader.read(DxfWriter.write(imported.document))
        assertEquals(1, exported.document.entities.size)
        assertTrue(exported.unsupportedEntityCounts.isEmpty())
    }

    @Test fun exportIsPureAsciiWithUnicodeEscapesAndEndsWithEof() {
        val out = DxfWriter.write(sample)
        assertTrue(out.all { it.code < 128 })
        assertTrue(out.contains("\\U+0645"))
        assertTrue(out.trimEnd().endsWith("EOF"))
        assertTrue(out.contains("AC1015"))
    }

    @Test fun numbersNeverUseExponentNotation() {
        val doc = DxfDocument(6, emptyList(), listOf(DxfLine("0", DxfPoint(1.0E-7, 123456789.5), DxfPoint(0.0, 1.0E10))))
        val out = DxfWriter.write(doc)
        assertFalse(out.contains("E-") || out.contains("E+") || out.contains("e-"))
        assertEquals(doc.entities, DxfReader.read(out).document.entities)
    }

    @Test fun rejectsInvalidDocumentsInsteadOfChangingThemSilently() {
        fun bad(entity: DxfEntity, layers: List<String> = emptyList()) =
            assertThrows(IllegalArgumentException::class.java) { DxfWriter.write(DxfDocument(6, layers, listOf(entity))) }
        bad(DxfLine("0", DxfPoint(Double.NaN, 0.0), DxfPoint(1.0, 1.0)))
        bad(DxfLine("", DxfPoint(0.0, 0.0), DxfPoint(1.0, 1.0)))
        bad(DxfLine("A/B", DxfPoint(0.0, 0.0), DxfPoint(1.0, 1.0)))
        bad(DxfCircle("0", DxfPoint(0.0, 0.0), 0.0))
        bad(DxfArc("0", DxfPoint(0.0, 0.0), -1.0, 0.0, 90.0))
        bad(DxfPolyline("0", listOf(DxfVertex(0.0, 0.0)), closed = false))
        bad(DxfText("0", DxfPoint(0.0, 0.0), 0.2, "سطر\nثان"))
        bad(DxfText("0", DxfPoint(0.0, 0.0), 0.0, "نص"))
        bad(DxfLine("0", DxfPoint(0.0, 0.0), DxfPoint(1.0, 1.0)), layers = listOf(" bad "))
    }
}
