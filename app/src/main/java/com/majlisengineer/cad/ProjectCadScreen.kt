package com.majlisengineer.cad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majlisengineer.data.CadDrawingEntity
import com.majlisengineer.data.CadLayerEntity
import com.majlisengineer.data.MaterialsDao
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class CadTool(val title: String, val type: String) {
    Select("تحديد", "SELECT"), Line("خط", "LINE"), Rectangle("مستطيل", "RECT"),
    Circle("دائرة", "CIRCLE"), Text("نص", "TEXT")
}
private const val CAD_PIXELS_PER_METER = 56f
private const val CAD_GRID_METERS = 0.5

@Composable
fun ProjectCadScreen(dao: MaterialsDao, projectId: String, modifier: Modifier = Modifier) {
    val layers by dao.observeCadLayers(projectId).collectAsState(initial = emptyList())
    val entities by dao.observeCadEntities(projectId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    var tool by remember { mutableStateOf(CadTool.Select) }
    var layerId by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CadDrawingEntity?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var editing by remember { mutableStateOf<CadDrawingEntity?>(null) }
    var addLayer by remember { mutableStateOf(false) }
    var snapToGrid by remember { mutableStateOf(true) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragEntity by remember { mutableStateOf<CadDrawingEntity?>(null) }
    var isPanning by remember { mutableStateOf(false) }

    LaunchedEffect(layers.size, projectId) {
        if (layers.isEmpty()) {
            val defaults = listOf("معماري", "كهرباء وإنارة", "سباكة", "تكييف وتهوية", "تشطيبات", "أثاث")
            dao.insertCadLayers(defaults.mapIndexed { index, name ->
                val colors = listOf("#285C50", "#D69B35", "#2876B8", "#7A62B3", "#B86A55", "#66717C")
                CadLayerEntity(UUID.randomUUID().toString(), projectId, name, colors[index], true, index)
            })
        } else if (layerId == null || layers.none { it.id == layerId }) layerId = layers.first().id
    }

    val visibleLayers = layers.filter { it.visible }.map { it.id }.toSet()
    val visibleEntities = entities.filter { it.layerId in visibleLayers }
    val selected = entities.firstOrNull { it.id == selectedId }
    val renderItems = if (preview == null) visibleEntities else visibleEntities.map { if (it.id == preview!!.id) preview!! else it }

    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("أدوات الرسم الهندسي 2D", fontWeight = FontWeight.Bold)
                Text("${entities.size} عنصر • ${layers.size} طبقة • المقياس: ${(zoom * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(8f) }) { Text("＋") }
                TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.2f) }) { Text("－") }
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("ملاءمة") }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items(CadTool.values().toList()) { item ->
                FilterChip(selected = tool == item, onClick = { tool = item }, label = { Text(item.title) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = snapToGrid, onCheckedChange = { snapToGrid = it })
            Text("التقاط للشبكة (0.5 م)", fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            TextButton(enabled = selected != null, onClick = { editing = selected }) { Text("خصائص") }
            TextButton(enabled = selected != null, onClick = {
                selected?.let { entity -> scope.launch { dao.deleteCadEntity(entity.id) } }
                selectedId = null
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items(layers) { layer ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = layerId == layer.id, onClick = { layerId = layer.id }, label = { Text(layer.name) })
                    Checkbox(checked = layer.visible, onCheckedChange = { visible ->
                        scope.launch { dao.updateCadLayer(layer.copy(visible = visible)) }
                        if (!visible && selected?.layerId == layer.id) selectedId = null
                    })
                }
            }
            item { OutlinedButton(onClick = { addLayer = true }) { Text("＋ طبقة") } }
        }
        Text(
            when (tool) {
                CadTool.Select -> "التحديد: اسحب العنصر لتحريكه، أو اسحب مساحة فارغة لتحريك اللوحة"
                CadTool.Line -> "الخط: اسحب من نقطة البداية إلى نقطة النهاية"
                CadTool.Rectangle -> "المستطيل: اسحب لتحديد الركن المقابل"
                CadTool.Circle -> "الدائرة: اسحب لتحديد القطر"
                CadTool.Text -> "النص: المس موضع إدراج النص"
            },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF9F7FB), RoundedCornerShape(12.dp))
                .pointerInput(tool, layerId, layers, visibleEntities, zoom, pan, snapToGrid) {
                    detectTapGestures { tap ->
                        val scale = CAD_PIXELS_PER_METER * zoom
                        var world = Offset((tap.x - size.width / 2f - pan.x) / scale, (tap.y - size.height / 2f - pan.y) / scale)
                        if (snapToGrid && tool != CadTool.Select) world = snapPoint(world)
                        if (tool == CadTool.Select) {
                            selectedId = findCadHit(visibleEntities, world, 0.2 / zoom)?.id
                        } else if (tool == CadTool.Text) {
                            val targetLayer = layerId ?: layers.firstOrNull()?.id ?: return@detectTapGestures
                            val item = CadDrawingEntity(UUID.randomUUID().toString(), projectId, targetLayer, "TEXT",
                                world.x.toDouble(), world.y.toDouble(), 1.5, 0.45, "نص جديد")
                            scope.launch { dao.insertCadEntity(item) }
                            selectedId = item.id
                            tool = CadTool.Select
                        }
                    }
                }
                .pointerInput(tool, layerId, visibleEntities, zoom, pan, snapToGrid) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val scale = CAD_PIXELS_PER_METER * zoom
                            val world = Offset((start.x - size.width / 2f - pan.x) / scale, (start.y - size.height / 2f - pan.y) / scale)
                            dragOrigin = if (snapToGrid && tool != CadTool.Select) snapPoint(world) else world
                            if (tool == CadTool.Select) {
                                dragEntity = findCadHit(visibleEntities, world, 0.25 / zoom)
                                selectedId = dragEntity?.id
                                isPanning = dragEntity == null
                                preview = dragEntity
                            } else {
                                isPanning = false
                                val targetLayer = layerId ?: layers.firstOrNull()?.id
                                if (targetLayer != null && tool != CadTool.Text) {
                                    preview = CadDrawingEntity(UUID.randomUUID().toString(), projectId, targetLayer, tool.type,
                                        dragOrigin.x.toDouble(), dragOrigin.y.toDouble(), 0.0, 0.0, "")
                                }
                            }
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val scale = CAD_PIXELS_PER_METER * zoom
                            if (tool == CadTool.Select) {
                                val original = dragEntity
                                if (original == null || isPanning) pan += delta
                                else preview = original.copy(
                                    x = original.x + delta.x / scale,
                                    y = original.y + delta.y / scale,
                                    updatedAt = System.currentTimeMillis()
                                )
                            } else {
                                val endRaw = Offset(dragOrigin.x + delta.x / scale, dragOrigin.y + delta.y / scale)
                                val end = if (snapToGrid) snapPoint(endRaw) else endRaw
                                val start = dragOrigin
                                val item = preview ?: return@detectDragGestures
                                preview = when (tool) {
                                    CadTool.Line -> item.copy(width = (end.x - start.x).toDouble(), height = (end.y - start.y).toDouble())
                                    CadTool.Rectangle -> item.copy(
                                        x = min(start.x, end.x).toDouble(), y = min(start.y, end.y).toDouble(),
                                        width = abs(end.x - start.x).toDouble(), height = abs(end.y - start.y).toDouble())
                                    CadTool.Circle -> {
                                        val diameter = max(abs(end.x - start.x), abs(end.y - start.y))
                                        item.copy(x = min(start.x, end.x).toDouble(), y = min(start.y, end.y).toDouble(),
                                            width = diameter.toDouble(), height = diameter.toDouble())
                                    }
                                    else -> item
                                }
                            }
                        },
                        onDragEnd = {
                            val item = preview
                            if (tool == CadTool.Select) {
                                if (!isPanning && item != null && dragEntity != null) scope.launch { dao.updateCadEntity(item) }
                            } else if (item != null && (abs(item.width) > 0.001 || abs(item.height) > 0.001)) {
                                scope.launch { dao.insertCadEntity(item) }
                                selectedId = item.id
                                tool = CadTool.Select
                            }
                            preview = null
                            dragEntity = null
                            isPanning = false
                        },
                        onDragCancel = { preview = null; dragEntity = null; isPanning = false }
                    )
                }
        ) {
            val center = Offset(size.width / 2f + pan.x, size.height / 2f + pan.y)
            val scale = CAD_PIXELS_PER_METER * zoom
            val grid = CAD_GRID_METERS.toFloat() * scale
            if (grid >= 5f) {
                var gx = center.x % grid
                while (gx < size.width) { drawLine(Color(0xFFE5E1E9), Offset(gx, 0f), Offset(gx, size.height), 0.7f); gx += grid }
                var gy = center.y % grid
                while (gy < size.height) { drawLine(Color(0xFFE5E1E9), Offset(0f, gy), Offset(size.width, gy), 0.7f); gy += grid }
            }
            renderItems.forEach { item ->
                val color = parseCadColor(layers.firstOrNull { it.id == item.layerId }?.colorHex)
                val p = Offset(center.x + item.x.toFloat() * scale, center.y + item.y.toFloat() * scale)
                val end = Offset(p.x + item.width.toFloat() * scale, p.y + item.height.toFloat() * scale)
                val left = min(p.x, end.x)
                val top = min(p.y, end.y)
                val w = max(2f, abs(end.x - p.x))
                val h = max(2f, abs(end.y - p.y))
                val stroke = if (item.id == selectedId) 4f else 2.5f
                when (item.type) {
                    "LINE" -> drawLine(color, p, end, strokeWidth = stroke)
                    "RECT" -> drawRect(color, Offset(left, top), Size(w, h), style = Stroke(stroke))
                    "CIRCLE" -> drawCircle(color, max(w, h) / 2f, Offset(left + w / 2f, top + h / 2f), style = Stroke(stroke))
                    "TEXT" -> drawText(textMeasurer, item.label.ifBlank { "نص" }, topLeft = p, style = TextStyle(color = color, fontSize = 14.sp))
                }
                if (item.id == selectedId) drawRect(Color(0xFF7B61A8), Offset(left - 5f, top - 5f), Size(w + 10f, h + 10f), style = Stroke(1.5f))
            }
        }
        Text("الرسم يحفظ تلقائيًا محليًا. الأدوات تدعم الآن الرسم بالسحب، التحريك، الطبقات، التكبير والتقاط الشبكة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editing?.let { item ->
        CadEntityDialog(item, { editing = null }) { label, width, height ->
            scope.launch { dao.updateCadEntity(item.copy(label = label, width = width, height = height, updatedAt = System.currentTimeMillis())) }
            editing = null
        }
    }
    if (addLayer) CadLayerDialog({ addLayer = false }) { name ->
        val layer = CadLayerEntity(UUID.randomUUID().toString(), projectId, name, "#285C50", true, layers.size)
        scope.launch { dao.insertCadLayer(layer) }
        layerId = layer.id
        addLayer = false
    }
}

private fun snapPoint(point: Offset): Offset = Offset(
    (kotlin.math.round(point.x / CAD_GRID_METERS) * CAD_GRID_METERS).toFloat(),
    (kotlin.math.round(point.y / CAD_GRID_METERS) * CAD_GRID_METERS).toFloat()
)

private fun findCadHit(items: List<CadDrawingEntity>, point: Offset, tolerance: Double): CadDrawingEntity? =
    items.asReversed().firstOrNull { item ->
        point.x.toDouble() in (min(item.x, item.x + item.width) - tolerance)..(max(item.x, item.x + item.width) + tolerance) &&
        point.y.toDouble() in (min(item.y, item.y + item.height) - tolerance)..(max(item.y, item.y + item.height) + tolerance)
    }

private fun parseCadColor(value: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(value ?: "#285C50")) }.getOrDefault(Color(0xFF285C50))

@Composable
private fun CadEntityDialog(entity: CadDrawingEntity, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var label by remember(entity.id) { mutableStateOf(entity.label) }
    var width by remember(entity.id) { mutableStateOf(entity.width.toString()) }
    var height by remember(entity.id) { mutableStateOf(entity.height.toString()) }
    val valid = (width.toDoubleOrNull() ?: -1.0) > 0.0 && (height.toDoubleOrNull() ?: -1.0) >= 0.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("خصائص عنصر الرسم") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("الوصف أو النص") }, singleLine = true)
            OutlinedTextField(value = width, onValueChange = { width = it }, label = { Text("العرض بالمتر") }, singleLine = true)
            OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("الارتفاع بالمتر") }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(label, width.toDouble(), height.toDouble()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun CadLayerDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("طبقة جديدة") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الطبقة") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}
