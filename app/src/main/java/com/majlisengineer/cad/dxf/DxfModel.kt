package com.majlisengineer.cad.dxf

/**
 * نموذج DXF نقي (بلا Android أو Room). يغطي فقط العناصر التي أثبتنا توافقها بالاختبار؛
 * راجع docs/technical-tests/dxf-support-matrix.md لمصفوفة المدعوم وغير المدعوم.
 */
enum class DxfUnits(val code: Int, val label: String, val metersPerUnit: Double?) {
    UNITLESS(0, "بدون وحدة", null),
    INCHES(1, "بوصة", 0.0254),
    FEET(2, "قدم", 0.3048),
    MILLIMETERS(4, "ملم", 0.001),
    CENTIMETERS(5, "سم", 0.01),
    METERS(6, "متر", 1.0);

    companion object {
        fun fromCode(code: Int): DxfUnits? = values().firstOrNull { it.code == code }
    }
}

data class DxfPoint(val x: Double, val y: Double)

/** bulge = tan(زاوية القوس / 4)؛ صفر يعني قطعة مستقيمة. */
data class DxfVertex(val x: Double, val y: Double, val bulge: Double = 0.0)

sealed class DxfEntity {
    abstract val layer: String
}

data class DxfLine(override val layer: String, val start: DxfPoint, val end: DxfPoint) : DxfEntity()

data class DxfPolyline(override val layer: String, val vertices: List<DxfVertex>, val closed: Boolean) : DxfEntity()

data class DxfCircle(override val layer: String, val center: DxfPoint, val radius: Double) : DxfEntity()

/** زوايا بالدرجات عكس عقارب الساعة من start إلى end. */
data class DxfArc(
    override val layer: String,
    val center: DxfPoint,
    val radius: Double,
    val startAngleDeg: Double,
    val endAngleDeg: Double
) : DxfEntity()

data class DxfText(
    override val layer: String,
    val position: DxfPoint,
    val height: Double,
    val text: String,
    val rotationDeg: Double = 0.0
) : DxfEntity()

data class DxfDocument(
    /** القيمة الخام لـ $INSUNITS؛ تُحفظ كما هي حتى لو لم نعرفها. */
    val unitsCode: Int,
    val layers: List<String>,
    val entities: List<DxfEntity>
) {
    val units: DxfUnits? get() = DxfUnits.fromCode(unitsCode)
}

data class DxfImportResult(
    val document: DxfDocument,
    /** عناصر لم تُستورد: المفتاح اسم النوع (أو وصفه) والقيمة عددها. لا يسقط أي عنصر دون ظهوره هنا. */
    val unsupportedEntityCounts: Map<String, Int>,
    /** أسماء الكتل (Blocks) المعرّفة في الملف والتي لم تُفكَّك (تستثنى كتل النظام مثل *Model_Space). */
    val userBlockNames: List<String>,
    /** عدد العناصر المدعومة التي أُهملت إحداثيتها Z. */
    val droppedZCount: Int,
    val warnings: List<String>,
    /** نص الملف الأصلي كما قُرئ؛ يجب على المستدعي الاحتفاظ به وعدم استبداله. */
    val originalText: String
) {
    val userBlockCount: Int get() = userBlockNames.size

    /** لا يجوز استخراج كميات قبل أن يؤكد المستخدم الوحدة والمقياس إن كانت الوحدات غير معروفة. */
    val requiresUnitConfirmation: Boolean get() = document.units == null || document.units == DxfUnits.UNITLESS
}

class DxfFormatException(message: String) : Exception(message)
