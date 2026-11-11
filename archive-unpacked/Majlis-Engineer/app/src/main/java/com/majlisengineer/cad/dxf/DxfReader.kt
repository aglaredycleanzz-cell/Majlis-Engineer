package com.majlisengineer.cad.dxf

/** قارئ DXF نصي (ASCII). الأخطاء البنيوية ترفع [DxfFormatException]؛ أما العنصر غير المدعوم أو غير الصالح فيُسجَّل ولا يُسقط بصمت. */
object DxfReader {
    private data class GroupPair(val code: Int, val value: String, val line: Int)
    private data class Record(val type: String, val pairs: List<GroupPair>)

    private val unicodeEscape = Regex("""\\U\+([0-9A-Fa-f]{4})""")
    private const val BINARY_SENTINEL = "AutoCAD Binary DXF"

    fun read(rawText: String): DxfImportResult {
        val text = rawText.removePrefix("\uFEFF")
        if (text.startsWith(BINARY_SENTINEL)) {
            throw DxfFormatException("ملف DXF ثنائي غير مدعوم؛ يلزم تحويله إلى DXF نصي")
        }
        val pairs = tokenize(text)
        val sections = splitSections(pairs)

        val unitsCode = readUnitsCode(sections["HEADER"].orEmpty())
        val tableLayers = readLayerTable(sections["TABLES"].orEmpty())
        val userBlocks = userBlockNames(sections["BLOCKS"].orEmpty())

        val entities = mutableListOf<DxfEntity>()
        val unsupported = linkedMapOf<String, Int>()
        var droppedZ = 0
        val records = toRecords(sections["ENTITIES"].orEmpty())
        var i = 0
        fun bump(key: String) { unsupported[key] = (unsupported[key] ?: 0) + 1 }

        while (i < records.size) {
            val rec = records[i]
            when (rec.type) {
                "LINE", "CIRCLE", "ARC", "TEXT", "LWPOLYLINE" -> {
                    if (isMirroredExtrusion(rec)) { bump("${rec.type} (اتجاه سحب معكوس)") }
                    else {
                        val parsed = parseSimple(rec)
                        if (parsed == null) bump("${rec.type} (قيم ناقصة أو غير صالحة)")
                        else {
                            entities += parsed.first
                            if (parsed.second) droppedZ++
                        }
                    }
                    i++
                }
                "POLYLINE" -> {
                    val end = findSeqEnd(records, i)
                    val vertices = records.subList(i + 1, end).filter { it.type == "VERTEX" }
                    val parsed = if (isMirroredExtrusion(rec)) null else parseOldPolyline(rec, vertices)
                    if (parsed == null) bump("POLYLINE (ثلاثي الأبعاد أو شبكة أو منحنى)")
                    else { entities += parsed.first; if (parsed.second) droppedZ++ }
                    i = end + 1
                }
                "INSERT" -> {
                    bump("INSERT")
                    val hasAttribs = rec.pairs.any { it.code == 66 && it.value.trim() == "1" }
                    i = if (hasAttribs) findSeqEnd(records, i) + 1 else i + 1
                }
                else -> { bump(rec.type); i++ }
            }
        }

        val layerOrder = linkedSetOf<String>()
        layerOrder.addAll(tableLayers)
        entities.forEach { layerOrder.add(it.layer) }

        val warnings = mutableListOf<String>()
        val units = DxfUnits.fromCode(unitsCode)
        if (units == null) warnings += "رمز وحدات الرسم ($unitsCode) غير مدعوم؛ يجب تأكيد الوحدة قبل استخراج أي كميات"
        else if (units == DxfUnits.UNITLESS) warnings += "وحدات الرسم غير محددة؛ يجب تأكيد الوحدة والمقياس قبل استخراج أي كميات"
        unsupported.forEach { (type, n) -> warnings += "لم يُستورد: $type × $n" }
        if (userBlocks.isNotEmpty()) warnings += "يحتوي الملف على ${userBlocks.size} كتلة (Block) لم تُفكَّك: ${userBlocks.joinToString("، ")}"
        if (droppedZ > 0) warnings += "أُهملت الإحداثية Z في $droppedZ عنصر"

        return DxfImportResult(
            document = DxfDocument(unitsCode, layerOrder.toList(), entities.toList()),
            unsupportedEntityCounts = unsupported.toMap(),
            userBlockNames = userBlocks,
            droppedZCount = droppedZ,
            warnings = warnings,
            originalText = rawText
        )
    }

    // ---------- tokenizing / structure ----------

    private fun tokenize(text: String): List<GroupPair> {
        val lines = text.split("\r\n", "\n", "\r").toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.size - 1)
        if (lines.isEmpty()) throw DxfFormatException("الملف فارغ")
        if (lines.size % 2 != 0) throw DxfFormatException("عدد أسطر الملف فردي؛ الملف تالف أو مقطوع")
        val out = ArrayList<GroupPair>(lines.size / 2)
        var i = 0
        while (i < lines.size) {
            val code = lines[i].trim().toIntOrNull()
                ?: throw DxfFormatException("رمز مجموعة غير صالح في السطر ${i + 1}")
            if (code != 999) out += GroupPair(code, lines[i + 1], i + 1)
            i += 2
        }
        return out
    }

    private fun splitSections(pairs: List<GroupPair>): Map<String, List<GroupPair>> {
        val result = linkedMapOf<String, List<GroupPair>>()
        var i = 0
        var sawEof = false
        while (i < pairs.size) {
            val p = pairs[i]
            if (p.code == 0 && p.value.trim() == "EOF") { sawEof = true; break }
            if (!(p.code == 0 && p.value.trim() == "SECTION")) {
                throw DxfFormatException("بنية غير متوقعة في السطر ${p.line}: توقعنا بداية قسم")
            }
            val nameP = pairs.getOrNull(i + 1)
            if (nameP == null || nameP.code != 2) throw DxfFormatException("قسم بلا اسم في السطر ${p.line}")
            var j = i + 2
            while (j < pairs.size && !(pairs[j].code == 0 && pairs[j].value.trim() == "ENDSEC")) j++
            if (j >= pairs.size) throw DxfFormatException("القسم ${nameP.value.trim()} لا ينتهي بـ ENDSEC؛ الملف مقطوع")
            result[nameP.value.trim()] = pairs.subList(i + 2, j)
            i = j + 1
        }
        if (!sawEof) throw DxfFormatException("لا توجد علامة EOF؛ الملف غير مكتمل")
        if (!result.containsKey("ENTITIES")) throw DxfFormatException("لا يوجد قسم ENTITIES")
        return result
    }

    private fun toRecords(pairs: List<GroupPair>): List<Record> {
        val out = mutableListOf<Record>()
        var type: String? = null
        var cur = mutableListOf<GroupPair>()
        for (p in pairs) {
            if (p.code == 0) {
                if (type != null) out += Record(type, cur)
                type = p.value.trim(); cur = mutableListOf()
            } else {
                if (type == null) throw DxfFormatException("بيانات قبل أول عنصر في السطر ${p.line}")
                cur += p
            }
        }
        if (type != null) out += Record(type, cur)
        return out
    }

    private fun findSeqEnd(records: List<Record>, from: Int): Int {
        var j = from + 1
        while (j < records.size && records[j].type != "SEQEND") j++
        if (j >= records.size) throw DxfFormatException("عنصر ${records[from].type} لا ينتهي بـ SEQEND؛ الملف مقطوع")
        return j
    }

    private fun readUnitsCode(header: List<GroupPair>): Int {
        for (i in header.indices) {
            if (header[i].code == 9 && header[i].value.trim() == "\$INSUNITS") {
                val v = header.getOrNull(i + 1)
                if (v != null && v.code == 70) return v.value.trim().toIntOrNull()
                    ?: throw DxfFormatException("قيمة \$INSUNITS غير صالحة في السطر ${v.line}")
            }
        }
        return 0
    }

    private fun readLayerTable(tables: List<GroupPair>): List<String> =
        toRecords(tables).filter { it.type == "LAYER" }
            .mapNotNull { r -> r.pairs.firstOrNull { it.code == 2 }?.value?.trim()?.takeIf { it.isNotEmpty() } }

    private fun userBlockNames(blocks: List<GroupPair>): List<String> =
        toRecords(blocks).filter { it.type == "BLOCK" }
            .mapNotNull { r -> r.pairs.firstOrNull { it.code == 2 }?.value?.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") && !it.equals("\$MODEL_SPACE", true) && !it.equals("\$PAPER_SPACE", true) }

    // ---------- entity parsing ----------

    private fun isMirroredExtrusion(rec: Record): Boolean {
        val z = rec.pairs.firstOrNull { it.code == 230 }?.value?.trim()?.toDoubleOrNull() ?: return false
        return z != 1.0
    }

    private fun num(rec: Record, code: Int): Double? =
        rec.pairs.firstOrNull { it.code == code }?.value?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun layerOf(rec: Record): String =
        rec.pairs.firstOrNull { it.code == 8 }?.value?.trim()?.ifEmpty { null } ?: "0"

    private fun nonZero(v: Double?): Boolean = v != null && v != 0.0

    /** يعيد (العنصر، هل أُهملت Z) أو null إذا كانت القيم ناقصة/غير صالحة. */
    private fun parseSimple(rec: Record): kotlin.Pair<DxfEntity, Boolean>? {
        val layer = layerOf(rec)
        return when (rec.type) {
            "LINE" -> {
                val x1 = num(rec, 10); val y1 = num(rec, 20); val x2 = num(rec, 11); val y2 = num(rec, 21)
                if (x1 == null || y1 == null || x2 == null || y2 == null) return null
                kotlin.Pair(DxfLine(layer, DxfPoint(x1, y1), DxfPoint(x2, y2)), nonZero(num(rec, 30)) || nonZero(num(rec, 31)))
            }
            "CIRCLE" -> {
                val x = num(rec, 10); val y = num(rec, 20); val r = num(rec, 40)
                if (x == null || y == null || r == null || r <= 0.0) return null
                kotlin.Pair(DxfCircle(layer, DxfPoint(x, y), r), nonZero(num(rec, 30)))
            }
            "ARC" -> {
                val x = num(rec, 10); val y = num(rec, 20); val r = num(rec, 40)
                val a0 = num(rec, 50); val a1 = num(rec, 51)
                if (x == null || y == null || r == null || r <= 0.0 || a0 == null || a1 == null) return null
                kotlin.Pair(DxfArc(layer, DxfPoint(x, y), r, a0, a1), nonZero(num(rec, 30)))
            }
            "TEXT" -> {
                val x = num(rec, 10); val y = num(rec, 20); val h = num(rec, 40)
                val content = rec.pairs.firstOrNull { it.code == 1 }?.value ?: return null
                if (x == null || y == null || h == null || h <= 0.0) return null
                kotlin.Pair(DxfText(layer, DxfPoint(x, y), h, decodeText(content), num(rec, 50) ?: 0.0), nonZero(num(rec, 30)))
            }
            "LWPOLYLINE" -> parseLwPolyline(rec, layer)
            else -> null
        }
    }

    private fun parseLwPolyline(rec: Record, layer: String): kotlin.Pair<DxfEntity, Boolean>? {
        val declared = rec.pairs.firstOrNull { it.code == 90 }?.value?.trim()?.toIntOrNull() ?: return null
        val flags = rec.pairs.firstOrNull { it.code == 70 }?.value?.trim()?.toIntOrNull() ?: 0
        val vs = mutableListOf<DxfVertex>()
        var pendingX: Double? = null
        for (p in rec.pairs) {
            when (p.code) {
                10 -> pendingX = p.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
                20 -> {
                    val x = pendingX ?: return null
                    val y = p.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
                    vs += DxfVertex(x, y); pendingX = null
                }
                42 -> {
                    val b = p.value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
                    if (vs.isEmpty()) return null
                    vs[vs.size - 1] = vs.last().copy(bulge = b)
                }
            }
        }
        if (vs.size != declared || vs.size < 2) return null
        return kotlin.Pair(DxfPolyline(layer, vs, closed = flags and 1 == 1), nonZero(num(rec, 38)))
    }

    private fun parseOldPolyline(rec: Record, vertexRecords: List<Record>): kotlin.Pair<DxfEntity, Boolean>? {
        val flags = rec.pairs.firstOrNull { it.code == 70 }?.value?.trim()?.toIntOrNull() ?: 0
        if (flags and (2 or 4 or 8 or 16 or 32 or 64) != 0) return null
        val vs = mutableListOf<DxfVertex>()
        var z = nonZero(num(rec, 30))
        for (v in vertexRecords) {
            val x = num(v, 10) ?: return null
            val y = num(v, 20) ?: return null
            if (nonZero(num(v, 30))) z = true
            vs += DxfVertex(x, y, num(v, 42) ?: 0.0)
        }
        if (vs.size < 2) return null
        return kotlin.Pair(DxfPolyline(layerOf(rec), vs, closed = flags and 1 == 1), z)
    }

    internal fun decodeText(s: String): String =
        unicodeEscape.replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
}
