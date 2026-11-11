package com.majlisengineer.cad.dxf

import java.math.BigDecimal

/**
 * كاتب DXF بصيغة R2000 (AC1015) النصية. صارم عمدًا: أي مستند غير صالح يرفع IllegalArgumentException
 * بدل أن يُعدَّل بصمت. النص غير ASCII يُكتب بتهريب \U+XXXX كما تفعل برامج CAD.
 */
object DxfWriter {
    private const val NL = "\r\n"
    private val forbiddenLayerChars = "<>/\\\":;?*|=`".toSet()

    fun write(doc: DxfDocument): String {
        validate(doc)
        val layers = (listOf("0") + doc.layers).distinct()
        val h = Handles()
        val tableLtype = h.next(); val tableLayer = h.next(); val tableStyle = h.next()
        val tableAppId = h.next(); val tableBlockRecord = h.next()
        val modelRecord = h.next(); val paperRecord = h.next()
        val rootDict = h.next()

        val out = StringBuilder()
        fun g(code: Int, value: String) { out.append(code.toString().padStart(3)).append(NL).append(value).append(NL) }
        fun g(code: Int, value: Double) = g(code, fmt(value))
        fun g(code: Int, value: Int) = g(code, value.toString())
        fun hex(v: Long) = v.toString(16).uppercase()

        // TABLES
        val tables = StringBuilder()
        run {
            val saved = out.length
            g(0, "SECTION"); g(2, "TABLES")
            // LTYPE
            g(0, "TABLE"); g(2, "LTYPE"); g(5, hex(tableLtype)); g(330, "0"); g(100, "AcDbSymbolTable"); g(70, 3)
            for (name in listOf("ByBlock", "ByLayer", "Continuous")) {
                g(0, "LTYPE"); g(5, hex(h.next())); g(330, hex(tableLtype))
                g(100, "AcDbSymbolTableRecord"); g(100, "AcDbLinetypeTableRecord")
                g(2, name); g(70, 0); g(3, if (name == "Continuous") "Solid line" else ""); g(72, 65); g(73, 0); g(40, 0.0)
            }
            g(0, "ENDTAB")
            // LAYER
            g(0, "TABLE"); g(2, "LAYER"); g(5, hex(tableLayer)); g(330, "0"); g(100, "AcDbSymbolTable"); g(70, layers.size)
            for (name in layers) {
                g(0, "LAYER"); g(5, hex(h.next())); g(330, hex(tableLayer))
                g(100, "AcDbSymbolTableRecord"); g(100, "AcDbLayerTableRecord")
                g(2, name); g(70, 0); g(62, 7); g(6, "Continuous")
            }
            g(0, "ENDTAB")
            // STYLE
            g(0, "TABLE"); g(2, "STYLE"); g(5, hex(tableStyle)); g(330, "0"); g(100, "AcDbSymbolTable"); g(70, 1)
            g(0, "STYLE"); g(5, hex(h.next())); g(330, hex(tableStyle))
            g(100, "AcDbSymbolTableRecord"); g(100, "AcDbTextStyleTableRecord")
            g(2, "Standard"); g(70, 0); g(40, 0.0); g(41, 1.0); g(50, 0.0); g(71, 0); g(42, 2.5); g(3, "txt"); g(4, "")
            g(0, "ENDTAB")
            // APPID
            g(0, "TABLE"); g(2, "APPID"); g(5, hex(tableAppId)); g(330, "0"); g(100, "AcDbSymbolTable"); g(70, 1)
            g(0, "APPID"); g(5, hex(h.next())); g(330, hex(tableAppId))
            g(100, "AcDbSymbolTableRecord"); g(100, "AcDbRegAppTableRecord"); g(2, "ACAD"); g(70, 0)
            g(0, "ENDTAB")
            // BLOCK_RECORD
            g(0, "TABLE"); g(2, "BLOCK_RECORD"); g(5, hex(tableBlockRecord)); g(330, "0"); g(100, "AcDbSymbolTable"); g(70, 2)
            for ((name, handle) in listOf("*Model_Space" to modelRecord, "*Paper_Space" to paperRecord)) {
                g(0, "BLOCK_RECORD"); g(5, hex(handle)); g(330, hex(tableBlockRecord))
                g(100, "AcDbSymbolTableRecord"); g(100, "AcDbBlockTableRecord"); g(2, name)
            }
            g(0, "ENDTAB")
            g(0, "ENDSEC")
            tables.append(out, saved, out.length)
            out.setLength(saved)
        }
        // BLOCKS
        val blocks = StringBuilder()
        run {
            val saved = out.length
            g(0, "SECTION"); g(2, "BLOCKS")
            for ((name, record) in listOf("*Model_Space" to modelRecord, "*Paper_Space" to paperRecord)) {
                g(0, "BLOCK"); g(5, hex(h.next())); g(330, hex(record)); g(100, "AcDbEntity"); g(8, "0")
                g(100, "AcDbBlockBegin"); g(2, name); g(70, 0); g(10, 0.0); g(20, 0.0); g(30, 0.0); g(3, name); g(1, "")
                g(0, "ENDBLK"); g(5, hex(h.next())); g(330, hex(record)); g(100, "AcDbEntity"); g(8, "0"); g(100, "AcDbBlockEnd")
            }
            g(0, "ENDSEC")
            blocks.append(out, saved, out.length)
            out.setLength(saved)
        }
        // ENTITIES
        val entities = StringBuilder()
        run {
            val saved = out.length
            g(0, "SECTION"); g(2, "ENTITIES")
            for (e in doc.entities) {
                g(0, when (e) {
                    is DxfLine -> "LINE"; is DxfPolyline -> "LWPOLYLINE"; is DxfCircle -> "CIRCLE"
                    is DxfArc -> "ARC"; is DxfText -> "TEXT"
                })
                g(5, hex(h.next())); g(330, hex(modelRecord)); g(100, "AcDbEntity"); g(8, e.layer)
                when (e) {
                    is DxfLine -> {
                        g(100, "AcDbLine"); g(10, e.start.x); g(20, e.start.y); g(30, 0.0)
                        g(11, e.end.x); g(21, e.end.y); g(31, 0.0)
                    }
                    is DxfPolyline -> {
                        g(100, "AcDbPolyline"); g(90, e.vertices.size); g(70, if (e.closed) 1 else 0)
                        for (v in e.vertices) { g(10, v.x); g(20, v.y); if (v.bulge != 0.0) g(42, v.bulge) }
                    }
                    is DxfCircle -> { g(100, "AcDbCircle"); g(10, e.center.x); g(20, e.center.y); g(30, 0.0); g(40, e.radius) }
                    is DxfArc -> {
                        g(100, "AcDbCircle"); g(10, e.center.x); g(20, e.center.y); g(30, 0.0); g(40, e.radius)
                        g(100, "AcDbArc"); g(50, e.startAngleDeg); g(51, e.endAngleDeg)
                    }
                    is DxfText -> {
                        g(100, "AcDbText"); g(10, e.position.x); g(20, e.position.y); g(30, 0.0); g(40, e.height)
                        g(1, encodeText(e.text)); if (e.rotationDeg != 0.0) g(50, e.rotationDeg); g(100, "AcDbText")
                    }
                }
            }
            g(0, "ENDSEC")
            entities.append(out, saved, out.length)
            out.setLength(saved)
        }
        // OBJECTS
        val objects = StringBuilder()
        run {
            val saved = out.length
            g(0, "SECTION"); g(2, "OBJECTS")
            g(0, "DICTIONARY"); g(5, hex(rootDict)); g(330, "0"); g(100, "AcDbDictionary"); g(281, 1)
            g(0, "ENDSEC")
            objects.append(out, saved, out.length)
            out.setLength(saved)
        }

        // HEADER + تركيب الأقسام
        g(0, "SECTION"); g(2, "HEADER")
        g(9, "\$ACADVER"); g(1, "AC1015")
        g(9, "\$DWGCODEPAGE"); g(3, "ANSI_1252")
        g(9, "\$HANDSEED"); g(5, hex(h.peek()))
        g(9, "\$INSUNITS"); g(70, doc.unitsCode)
        g(0, "ENDSEC")
        out.append(tables).append(blocks).append(entities).append(objects)
        g(0, "EOF")
        return out.toString()
    }

    private class Handles {
        private var n = 0x20L
        fun next(): Long = n++
        fun peek(): Long = n
    }

    private fun fmt(v: Double): String = BigDecimal.valueOf(v).toPlainString()

    private fun encodeText(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c.code in 32..126) sb.append(c)
            else sb.append("\\U+").append(c.code.toString(16).uppercase().padStart(4, '0'))
        }
        return sb.toString()
    }

    private fun validate(doc: DxfDocument) {
        require(doc.unitsCode >= 0) { "رمز الوحدات سالب" }
        for (l in doc.layers) validateLayer(l)
        for (e in doc.entities) {
            validateLayer(e.layer)
            when (e) {
                is DxfLine -> require(finite(e.start.x, e.start.y, e.end.x, e.end.y)) { "إحداثيات الخط غير صالحة" }
                is DxfPolyline -> {
                    require(e.vertices.size >= 2) { "الخط المتعدد يحتاج رأسين على الأقل" }
                    require(e.vertices.all { finite(it.x, it.y, it.bulge) }) { "رؤوس الخط المتعدد غير صالحة" }
                }
                is DxfCircle -> require(finite(e.center.x, e.center.y, e.radius) && e.radius > 0.0) { "الدائرة غير صالحة" }
                is DxfArc -> require(finite(e.center.x, e.center.y, e.radius, e.startAngleDeg, e.endAngleDeg) && e.radius > 0.0) { "القوس غير صالح" }
                is DxfText -> {
                    require(finite(e.position.x, e.position.y, e.height, e.rotationDeg) && e.height > 0.0) { "النص غير صالح" }
                    require(e.text.none { it == '\n' || it == '\r' || it.code < 32 }) { "نص TEXT سطر واحد ولا يقبل أسطرًا جديدة أو رموز تحكم" }
                }
            }
        }
    }

    private fun validateLayer(name: String) {
        require(name.isNotBlank() && name == name.trim()) { "اسم الطبقة فارغ أو بمسافات في أطرافه" }
        require(name.none { it in forbiddenLayerChars || it.code < 32 }) { "اسم الطبقة يحتوي رمزًا غير مسموح: $name" }
    }

    private fun finite(vararg v: Double) = v.all { it.isFinite() }
}
