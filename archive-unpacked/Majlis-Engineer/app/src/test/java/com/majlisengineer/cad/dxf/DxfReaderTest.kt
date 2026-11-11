package com.majlisengineer.cad.dxf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DxfReaderTest {
    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/dxf/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun lines(vararg l: String) = l.joinToString("\n", postfix = "\n")
    private fun file(entities: String, header: String = "") =
        lines("0", "SECTION", "2", "HEADER") + header + lines("0", "ENDSEC", "0", "SECTION", "2", "ENTITIES") +
            entities + lines("0", "ENDSEC", "0", "EOF")

    @Test fun readsSupportedEntitiesFromIndependentlyGeneratedR2000File() {
        val r = DxfReader.read(fixture("basic_r2000_meters.dxf"))
        assertEquals(DxfUnits.METERS, r.document.units)
        assertFalse(r.requiresUnitConfirmation)
        assertEquals(6, r.document.entities.size)
        assertTrue(r.unsupportedEntityCounts.isEmpty())
        assertTrue(r.document.layers.containsAll(listOf("Architectural", "Electrical")))

        val line = r.document.entities[0] as DxfLine
        assertEquals(DxfLine("Architectural", DxfPoint(0.0, 0.0), DxfPoint(6.0, 0.0)), line)
        val poly = r.document.entities[1] as DxfPolyline
        assertTrue(poly.closed)
        assertEquals(4, poly.vertices.size)
        assertEquals(0.5, poly.vertices[1].bulge, 0.0)
        assertEquals(DxfCircle("Electrical", DxfPoint(3.0, 2.0), 0.35), r.document.entities[2])
        assertEquals(DxfArc("Architectural", DxfPoint(1.0, 1.0), 0.9, 0.0, 90.0), r.document.entities[3])
        val arabic = r.document.entities[4] as DxfText
        assertEquals("مجلس رجال", arabic.text)
        assertEquals(15.0, arabic.rotationDeg, 0.0)
        assertEquals("Majlis A", (r.document.entities[5] as DxfText).text)
    }

    @Test fun reportsEveryUnsupportedEntityInsteadOfDroppingItSilently() {
        val text = fixture("unsupported_mix.dxf")
        val r = DxfReader.read(text)
        assertEquals(1, r.document.entities.size)
        val c = r.unsupportedEntityCounts
        assertEquals(2, c["INSERT"])
        for (type in listOf("MTEXT", "HATCH", "SPLINE", "ELLIPSE", "POINT", "DIMENSION")) assertEquals(type, 1, c[type])
        assertEquals(1, c.entries.single { it.key.startsWith("POLYLINE") }.value)
        assertEquals(9, c.values.sum())
        assertTrue("DOOR" in r.userBlockNames)
        assertTrue(r.warnings.any { it.contains("INSERT") })
        assertEquals(DxfUnits.MILLIMETERS, r.document.units)
        assertEquals("الملف الأصلي يبقى كما هو", text, r.originalText)
    }

    @Test fun readsOldStylePolylineAndFlagsUnitlessFilesForConfirmation() {
        val r = DxfReader.read(fixture("r12_polyline_unitless.dxf"))
        assertEquals(2, r.document.entities.size)
        val poly = r.document.entities[1] as DxfPolyline
        assertTrue(poly.closed)
        assertEquals(4, poly.vertices.size)
        assertEquals(DxfUnits.UNITLESS, r.document.units)
        assertTrue(r.requiresUnitConfirmation)
        assertTrue(r.userBlockNames.isEmpty())
    }

    @Test fun reportsDroppedZAndMirroredExtrusionNotSilentLoss() {
        val r = DxfReader.read(fixture("z_and_extrusion.dxf"))
        assertEquals(2, r.document.entities.size)
        assertEquals(1, r.droppedZCount)
        assertEquals(1, r.unsupportedEntityCounts.entries.single { it.key.startsWith("ARC") }.value)
        assertTrue(r.warnings.any { it.contains("Z") })
    }

    @Test fun unknownUnitCodeIsKeptAndRequiresConfirmation() {
        val r = DxfReader.read(file(lines("0", "LINE", "10", "0", "20", "0", "11", "1", "21", "1"), lines("9", "\$INSUNITS", "70", "3")))
        assertEquals(3, r.document.unitsCode)
        assertEquals(null, r.document.units)
        assertTrue(r.requiresUnitConfirmation)
    }

    @Test fun decodesUnicodeEscapesInBothCasesAndIgnoresCommentsAndCrLf() {
        val raw = "999\r\nمن أداة ما\r\n0\r\nSECTION\r\n2\r\nENTITIES\r\n0\r\nTEXT\r\n8\r\nA\r\n10\r\n1\r\n20\r\n2\r\n40\r\n0.5\r\n" +
            "1\r\n\\U+0645\\U+062d\\U+0628 ok\r\n0\r\nENDSEC\r\n0\r\nEOF\r\n"
        val r = DxfReader.read(raw)
        assertEquals("محب ok", (r.document.entities.single() as DxfText).text)
    }

    @Test fun invalidEntityValuesAreReportedNotSilentlyDropped() {
        val bad = lines("0", "LINE", "10", "0", "20", "0", "11", "1") +                 // ينقصه 21
            lines("0", "CIRCLE", "10", "0", "20", "0", "40", "-2") +                      // نصف قطر سالب
            lines("0", "LWPOLYLINE", "90", "3", "70", "0", "10", "0", "20", "0", "10", "1", "20", "1") + // العدد المعلن 3 والفعلي 2
            lines("0", "LINE", "10", "0", "20", "0", "11", "1", "21", "1")                // سليم
        val r = DxfReader.read(file(bad))
        assertEquals(1, r.document.entities.size)
        assertEquals(3, r.unsupportedEntityCounts.values.sum())
    }

    @Test fun rejectsStructurallyCorruptFiles() {
        fun bad(text: String) = assertThrows(DxfFormatException::class.java) { DxfReader.read(text) }
        bad("")
        bad("AutoCAD Binary DXF\r\n\u001a\u0000")
        bad(file(lines("0", "LINE", "10", "0")).removeSuffix(lines("0", "EOF")))                       // بلا EOF
        bad(file(lines("0", "LINE", "10", "0")).removeSuffix("0\nEOF\n") + "0\n")                       // عدد أسطر فردي
        bad(file(lines("0", "LINE", "10", "0")).replace("10\n0\n", "ab\n0\n"))                          // رمز مجموعة غير رقمي
        bad(lines("0", "SECTION", "2", "ENTITIES", "0", "LINE", "8", "0") + lines("0", "EOF"))           // قسم بلا ENDSEC
        bad(file(lines("0", "POLYLINE", "70", "0", "0", "VERTEX", "10", "0", "20", "0")))                // POLYLINE بلا SEQEND
        bad(lines("0", "SECTION", "2", "HEADER", "0", "ENDSEC", "0", "EOF"))                             // بلا ENTITIES
    }
}
