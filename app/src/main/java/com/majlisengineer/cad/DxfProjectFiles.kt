package com.majlisengineer.cad

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.majlisengineer.data.CadDrawingEntity
import com.majlisengineer.data.CadLayerEntity
import com.majlisengineer.data.DxfImportRecordEntity
import com.majlisengineer.data.MaterialsDao
import com.majlisengineer.cad.dxf.DxfArc
import com.majlisengineer.cad.dxf.DxfCircle
import com.majlisengineer.cad.dxf.DxfDocument
import com.majlisengineer.cad.dxf.DxfLine
import com.majlisengineer.cad.dxf.DxfPolyline
import com.majlisengineer.cad.dxf.DxfReader
import com.majlisengineer.cad.dxf.DxfText
import com.majlisengineer.cad.dxf.DxfUnits
import com.majlisengineer.cad.dxf.DxfWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

private const val MAX_DXF_BYTES = 10 * 1024 * 1024

private data class PendingDxf(
    val fileName: String,
    val originalBytes: ByteArray,
    val result: com.majlisengineer.cad.dxf.DxfImportResult
)

@Composable
fun DxfProjectFiles(
    dao: MaterialsDao,
    projectId: String,
    layers: List<CadLayerEntity>,
    entities: List<CadDrawingEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by dao.observeDxfImports(projectId).collectAsState(initial = emptyList())
    var pending by remember { mutableStateOf<PendingDxf?>(null) }
    var chosenUnits by remember { mutableStateOf(DxfUnits.METERS) }
    var unitsConfirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { withContext(Dispatchers.IO) { readDxf(context, uri) } }
                    .onSuccess { loaded ->
                        pending = loaded
                        chosenUnits = loaded.result.document.units?.takeIf { it.metersPerUnit != null } ?: DxfUnits.METERS
                        unitsConfirmed = !loaded.result.requiresUnitConfirmation
                    }
                    .onFailure { error ->
                        Toast.makeText(context, error.message ?: "تعذر قراءة ملف DXF", Toast.LENGTH_LONG).show()
                    }
                busy = false
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/dxf")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching {
                    withContext(Dispatchers.IO) {
                        val (document, notes) = cadToDxf(entities, layers)
                        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.US_ASCII).use { it.write(DxfWriter.write(document)) }
                        notes
                    }
                }.onSuccess { notes ->
                    val message = if (notes.isEmpty()) "تم تصدير DXF بوحدات المتر" else "تم التصدير؛ راجع التنبيه: ${notes.joinToString("؛ ")}"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }.onFailure { error ->
                    Toast.makeText(context, "تعذر تصدير DXF: ${error.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
                busy = false
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("ملفات DXF — الاستيراد يضيف العناصر المدعومة إلى الرسم ولا يستبدله.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { importPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), enabled = !busy) { Text(if (busy) "جارٍ الفحص…" else "استيراد / فحص DXF") }
            OutlinedButton(onClick = { exportPicker.launch("${projectId.take(8)}-drawing.dxf") }, modifier = Modifier.weight(1f), enabled = entities.isNotEmpty() && !busy) { Text(if (busy) "جارٍ المعالجة…" else "تصدير الرسم") }
        }
        history.take(3).forEach { record ->
            Text("أصل محفوظ: ${record.originalFileName} — أُضيف ${record.importedEntityCount} عنصر، وتُرك ${record.omittedEntityCount} خارج الرسم. ${record.conversionNotes}")
        }
    }

    pending?.let { imported ->
        val result = imported.result
        val convertible = result.document.entities.count { it is DxfLine || it is DxfCircle || it is DxfText }
        val omitted = result.document.entities.size - convertible + result.unsupportedEntityCounts.values.sum()
        val effectiveUnits = result.document.units?.takeIf { it.metersPerUnit != null } ?: chosenUnits
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("فحص DXF قبل الاستيراد") },
            text = {
                Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("الملف: ${imported.fileName}")
                    Text("العناصر المقروءة: ${result.document.entities.size}؛ التي يمكن إضافتها للرسم الحالي: $convertible؛ التي لن تُضاف: $omitted.")
                    Text("الأصل سيُحفظ داخل بيانات التطبيق الخاصة قبل أي تعديل. التصدير لاحقًا يضم العناصر التي يدعمها محرر التطبيق فقط.")
                    if (result.requiresUnitConfirmation) {
                        Text("وحدة الملف مجهولة أو بلا وحدة. اختر الوحدة يدويًا؛ ستُحوّل الأبعاد إلى المتر قبل الإدراج.")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            DxfUnits.values().filter { it.metersPerUnit != null }.forEach { unit ->
                                FilterChip(selected = chosenUnits == unit && unitsConfirmed, onClick = {
                                    chosenUnits = unit
                                    unitsConfirmed = true
                                }, label = { Text(unit.label) })
                            }
                        }
                    } else {
                        Text("الوحدة المسجلة: ${result.document.units?.label ?: "غير معروفة"}. ستحوّل الأبعاد إلى المتر.")
                    }
                    if (result.warnings.isNotEmpty()) {
                        Text("تحذيرات الاستيراد:")
                        result.warnings.forEach { Text("• $it") }
                    }
                    if (result.document.entities.any { it is DxfArc || it is DxfPolyline }) {
                        Text("القوس والخط المتعدد يقرؤهما محرك الفحص، لكن محرر المشروع لا يخزنهما بعد؛ لن يُستوردا. احتفظ بالأصل إذا احتجت هذه الهندسة.")
                    }
                    val rotatedTextCount = result.document.entities.filterIsInstance<DxfText>().count { it.rotationDeg != 0.0 }
                    if (rotatedTextCount > 0) Text("دوران النص غير مدعوم في نموذج التطبيق؛ ستُستورد النصوص دون دوران (${rotatedTextCount} نص).")
                    if (result.unsupportedEntityCounts.isNotEmpty()) Text("أنواع عناصر غير مدعومة: ${result.unsupportedEntityCounts.entries.joinToString("، ") { "${it.key} × ${it.value}" }}")
                    if (result.userBlockNames.isNotEmpty()) Text("كتل غير مفككة: ${result.userBlockNames.joinToString("، ")}")
                    if (result.droppedZCount > 0) Text("تنبيه: أُهملت إحداثيات الارتفاع Z في ${result.droppedZCount} عنصر.")
                }
            },
            confirmButton = {
                TextButton(enabled = convertible > 0 && effectiveUnits.metersPerUnit != null && unitsConfirmed, onClick = {
                    val accepted = imported
                    pending = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { saveAndImport(context, dao, projectId, layers, accepted, effectiveUnits) } }
                        .onSuccess { Toast.makeText(context, "حُفظ الأصل وأُضيف $it عنصر إلى الرسم.", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, "تعذر إكمال الاستيراد: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("حفظ الأصل وإضافة المدعوم") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } }
        )
    }
}

private fun readDxf(context: Context, uri: Uri): PendingDxf {
    val output = ByteArrayOutputStream()
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_DXF_BYTES) { "حجم DXF يتجاوز الحد الآمن البالغ 10 ميبيبايت" }
            output.write(buffer, 0, count)
        }
    } ?: error("تعذر فتح الملف المحدد")
    val bytes = output.toByteArray()
    require(bytes.all { (it.toInt() and 0xff) < 0x80 }) {
        "هذا الإصدار يقرأ DXF النصي ASCII فقط؛ الملف الأصلي لم يُعدل. حوّله إلى DXF ASCII ثم أعد المحاولة."
    }
    val text = bytes.toString(Charsets.US_ASCII)
    val result = DxfReader.read(text)
    val name = queryDisplayName(context, uri) ?: "import-${System.currentTimeMillis()}.dxf"
    return PendingDxf(name, bytes, result)
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private suspend fun saveAndImport(
    context: Context,
    dao: MaterialsDao,
    projectId: String,
    existingLayers: List<CadLayerEntity>,
    pending: PendingDxf,
    units: DxfUnits
): Int {
    val metersPerUnit = requireNotNull(units.metersPerUnit)
    val directory = File(context.filesDir, "dxf-originals/$projectId")
    check(directory.mkdirs() || directory.isDirectory) { "تعذر إنشاء مساحة حفظ الملف الأصلي" }
    val safeName = pending.fileName.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "drawing.dxf" }
    val original = File(directory, "${System.currentTimeMillis()}-$safeName")
    original.writeBytes(pending.originalBytes)

    val layerByName = existingLayers.associateBy { it.name }.toMutableMap()
    val newLayers = mutableListOf<CadLayerEntity>()
    fun layerId(name: String): String {
        val existing = layerByName[name]
        if (existing != null) return existing.id
        val layer = CadLayerEntity(UUID.randomUUID().toString(), projectId, name, "#285C50", true, existingLayers.size + newLayers.size)
        newLayers += layer
        layerByName[name] = layer
        return layer.id
    }
    val imported = pending.result.document.entities.mapNotNull { entity ->
        val layer = layerId(entity.layer)
        when (entity) {
            is DxfLine -> CadDrawingEntity(UUID.randomUUID().toString(), projectId, layer, "LINE",
                entity.start.x * metersPerUnit, -entity.start.y * metersPerUnit,
                (entity.end.x - entity.start.x) * metersPerUnit, -(entity.end.y - entity.start.y) * metersPerUnit)
            is DxfCircle -> {
                val diameter = entity.radius * 2.0 * metersPerUnit
                CadDrawingEntity(UUID.randomUUID().toString(), projectId, layer, "CIRCLE",
                    entity.center.x * metersPerUnit - diameter / 2.0, -entity.center.y * metersPerUnit - diameter / 2.0,
                    diameter, diameter)
            }
            is DxfText -> {
                val height = entity.height * metersPerUnit
                CadDrawingEntity(UUID.randomUUID().toString(), projectId, layer, "TEXT",
                    entity.position.x * metersPerUnit, -entity.position.y * metersPerUnit,
                    max(height * 4.0, 0.1), height, entity.text)
            }
            is DxfArc, is DxfPolyline -> null
        }
    }
    val omitted = pending.result.document.entities.size - imported.size + pending.result.unsupportedEntityCounts.values.sum()
    val rotatedTextCount = pending.result.document.entities.filterIsInstance<DxfText>().count { it.rotationDeg != 0.0 }
    val notes = buildList {
        if (rotatedTextCount > 0) add("دوران النص أُهمل في $rotatedTextCount عنصر.")
        if (pending.result.droppedZCount > 0) add("الإحداثية Z أُهملت في ${pending.result.droppedZCount} عنصر.")
        if (pending.result.unsupportedEntityCounts.isNotEmpty()) add("عناصر غير مدعومة: ${pending.result.unsupportedEntityCounts.entries.joinToString("، ") { "${it.key} × ${it.value}" }}.")
    }.joinToString(" ")
    val record = DxfImportRecordEntity(UUID.randomUUID().toString(), projectId, pending.fileName,
        original.absolutePath, pending.result.document.unitsCode, imported.size, omitted, notes)
    dao.insertDxfImportTransaction(newLayers, imported, record)
    return imported.size
}

private fun cadToDxf(entities: List<CadDrawingEntity>, layers: List<CadLayerEntity>): Pair<DxfDocument, List<String>> {
    val namesById = layers.associate { it.id to it.name }
    val warnings = mutableListOf<String>()
    val usedLayers = entities.mapNotNull { namesById[it.layerId] }.distinct()
    val safeNames = usedLayers.mapIndexed { index, name ->
        val safe = if (name.all { it.code in 32..126 }) name else "LAYER_${(index + 1).toString().padStart(2, '0')}"
        if (safe != name) warnings += "اسم الطبقة '$name' صُدّر باسم '$safe' لتوافق ASCII"
        name to safe
    }.toMap()
    val converted = entities.mapNotNull { item ->
        val layer = safeNames[namesById[item.layerId]] ?: "0"
        when (item.type) {
            "LINE" -> DxfLine(layer, com.majlisengineer.cad.dxf.DxfPoint(item.x, -item.y), com.majlisengineer.cad.dxf.DxfPoint(item.x + item.width, -(item.y + item.height)))
            "RECT" -> DxfPolyline(layer, listOf(
                com.majlisengineer.cad.dxf.DxfVertex(item.x, -item.y),
                com.majlisengineer.cad.dxf.DxfVertex(item.x + item.width, -item.y),
                com.majlisengineer.cad.dxf.DxfVertex(item.x + item.width, -(item.y + item.height)),
                com.majlisengineer.cad.dxf.DxfVertex(item.x, -(item.y + item.height))
            ), closed = true)
            "CIRCLE" -> DxfCircle(layer,
                com.majlisengineer.cad.dxf.DxfPoint(item.x + item.width / 2.0, -(item.y + item.height / 2.0)),
                max(item.width, item.height) / 2.0)
            "TEXT" -> DxfText(layer, com.majlisengineer.cad.dxf.DxfPoint(item.x, -item.y), max(item.height, 0.01), item.label)
            else -> { warnings += "العنصر ${item.type} غير مدعوم في التصدير"; null }
        }
    }
    val document = DxfDocument(
        unitsCode = DxfUnits.METERS.code,
        layers = safeNames.values.toList(),
        entities = converted
    )
    return document to warnings.distinct()
}
