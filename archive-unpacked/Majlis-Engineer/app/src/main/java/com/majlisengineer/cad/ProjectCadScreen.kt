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
import kotlin.math.max

private enum class CadTool(val title: String, val type: String) {
    Select("تحديد", "SELECT"), Line("خط", "LINE"), Rectangle("مستطيل", "RECT"),
    Circle("دائرة", "CIRCLE"), Text("نص", "TEXT")
}
private const val CAD_PIXELS_PER_METER = 56f

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

    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("محرر الرسم ثنائي الأبعاد", fontWeight = FontWeight.Bold)
                Text(entities.size.toString() + " عنصر • " + layers.size.toString() + " طبقة • الوحدة: متر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(4f) }) { Text("＋") }
                Text((zoom * 100).toInt().toString() + "%", fontSize = 11.sp)
                TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.35f) }) { Text("－") }
                TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("إعادة") }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(CadTool.values().toList()) { item ->
                FilterChip(selected = tool == item, onClick = { tool = item }, label = { Text(item.title) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            items(layers) { layer ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = layerId == layer.id, onClick = { layerId = layer.id; tool = CadTool.Select }, label = { Text(layer.name) })
                    Checkbox(checked = layer.visible, onCheckedChange = { visible ->
                        scope.launch { dao.updateCadLayer(layer.copy(visible = visible)) }
                        if (!visible && selected?.layerId == layer.id) selectedId = null
                    })
                }
            }
            item { OutlinedButton(onClick = { addLayer = true }) { Text("＋ طبقة") } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (tool) {
                    CadTool.Select -> "اسحب عنصرًا لتحريكه أو اسحب مساحة فارغة لتحريك اللوحة"
                    CadTool.Line -> "المس اللوحة لإضافة خط"
                    CadTool.Rectangle -> "المس اللوحة لإضافة مستطيل"
                    CadTool.Circle -> "المس اللوحة لإضافة دائرة"
                    CadTool.Text -> "المس اللوحة لإضافة عنصر نصي"
                },
                fontSize = 11.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(enabled = selected != null, onClick = { editing = selected }) { Text("تعديل") }
            TextButton(enabled = selected != null, onClick = {
                selected?.let { scope.launch { dao.deleteCadEntity(it.id) }; selectedId = null }
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        }
        Canvas(
            Modifier.fillMaxWidth().weight(1f).background(Color(0xFFF9F7FB), RoundedCornerShape(12.dp))
                .pointerInput(tool, layerId, layers, entities, zoom, pan) {
                    detectTapGestures { tap ->
                        val scale = CAD_PIXELS_PER_METER * zoom
                        val world = Offset((tap.x - size.width / 2f - pan.x) / scale, (tap.y - size.height / 2f - pan.y) / scale)
                        if (tool == CadTool.Select) selectedId = findCadHit(visibleEntities, world, 0.22 / zoom)?.id
                        else {
                            val targetLayer = layerId ?: layers.firstOrNull()?.id ?: return@detectTapGestures
                            val dims = when (tool) {
                                CadTool.Line -> 2.0 to 0.0
                                CadTool.Rectangle -> 2.0 to 1.2
                                CadTool.Circle -> 1.2 to 1.2
                                CadTool.Text -> 1.5 to 0.45
                                CadTool.Select -> 0.0 to 0.0
                            }
                            val item = CadDrawingEntity(UUID.randomUUID().toString(), projectId, targetLayer, tool.type,
                                world.x.toDouble(), world.y.toDouble(), dims.first, dims.second,
                                if (tool == CadTool.Text) "نص جديد" else "")
                            scope.launch { dao.insertCadEntity(item) }
                            selectedId = item.id
                            tool = CadTool.Select
                        }
                    }
                }
                .pointerInput(visibleEntities, zoom, pan, tool) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val scale = CAD_PIXELS_PER_METER * zoom
                            val world = Offset((start.x - size.width / 2f - pan.x) / scale, (start.y - size.height / 2f - pan.y) / scale)
                            val hit = if (tool == CadTool.Select) findCadHit(visibleEntities, world, 0.25 / zoom) else null
                            selectedId = hit?.id
                            preview = hit
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            val item = preview
                            if (item == null || tool != CadTool.Select) pan = pan + delta
                            else {
                                val scale = CAD_PIXELS_PER_METER * zoom
                                preview = item.copy(x = item.x + delta.x / scale, y = item.y + delta.y / scale,
                                    updatedAt = System.currentTimeMillis())
                            }
                        },
                        onDragEnd = { preview?.let { scope.launch { dao.updateCadEntity(it) } }; preview = null },
                        onDragCancel = { preview = null }
                    )
                }
        ) {
            val center = Offset(size.width / 2f + pan.x, size.height / 2f + pan.y)
            val scale = CAD_PIXELS_PER_METER * zoom
            val grid = 0.5f * scale
            if (grid >= 8f) {
                var gx = center.x % grid
                while (gx < size.width) { drawLine(Color(0xFFE9E5ED), Offset(gx, 0f), Offset(gx, size.height), 0.7f); gx += grid }
                var gy = center.y % grid
                while (gy < size.height) { drawLine(Color(0xFFE9E5ED), Offset(0f, gy), Offset(size.width, gy), 0.7f); gy += grid }
            }
            renderItems.forEach { item ->
                val color = parseCadColor(layers.firstOrNull { it.id == item.layerId }?.colorHex)
                val p = Offset(center.x + item.x.toFloat() * scale, center.y + item.y.toFloat() * scale)
                val s = Size(max(2f, item.width.toFloat() * scale), max(2f, item.height.toFloat() * scale))
                val stroke = if (item.id == selectedId) 4f else 2.5f
                when (item.type) {
                    "LINE" -> drawLine(color, p, p + Offset(s.width, item.height.toFloat() * scale), strokeWidth = stroke)
                    "RECT" -> drawRect(color, p, s, style = Stroke(stroke))
                    "CIRCLE" -> drawCircle(color, s.width / 2f, p + Offset(s.width / 2f, s.height / 2f), style = Stroke(stroke))
                    "TEXT" -> drawText(textMeasurer, item.label.ifBlank { "نص" }, topLeft = p, style = TextStyle(color = color, fontSize = 14.sp))
                }
                if (item.id == selectedId) drawRect(Color(0xFF7B61A8), p - Offset(5f, 5f), Size(s.width + 10f, s.height + 10f), style = Stroke(1.5f))
            }
        }
        Text("يحفظ الرسم تلقائيًا على الجهاز. محرر أولي 2D؛ دعم DXF والتحقق الهندسي مراحل لاحقة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun findCadHit(items: List<CadDrawingEntity>, point: Offset, tolerance: Double): CadDrawingEntity? =
    items.asReversed().firstOrNull { item ->
        point.x.toDouble() in (minOf(item.x, item.x + item.width) - tolerance)..(maxOf(item.x, item.x + item.width) + tolerance) &&
        point.y.toDouble() in (minOf(item.y, item.y + item.height) - tolerance)..(maxOf(item.y, item.y + item.height) + tolerance)
    }

private fun parseCadColor(value: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(value ?: "#285C50")) }.getOrDefault(Color(0xFF285C50))

@Composable
private fun CadEntityDialog(entity: CadDrawingEntity, onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var label by remember(entity.id) { mutableStateOf(entity.label) }
    var width by remember(entity.id) { mutableStateOf(entity.width.toString()) }
    var height by remember(entity.id) { mutableStateOf(entity.height.toString()) }
    val valid = (width.toDoubleOrNull() ?: -1.0) > 0.0 && (height.toDoubleOrNull() ?: -1.0) >= 0.0
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل عنصر الرسم") },
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
