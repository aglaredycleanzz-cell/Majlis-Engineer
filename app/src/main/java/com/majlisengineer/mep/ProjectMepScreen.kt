package com.majlisengineer.mep

import android.view.MotionEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.majlisengineer.data.MaterialsDao
import com.majlisengineer.data.MepElementEntity
import com.majlisengineer.data.SiteSurveyEntity
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_MEP_ELEMENTS_PER_SURVEY = 200
private const val MEP_SNAP_GRID_METRES = 0.05

private data class MepDrawingSpec(
    val system: MepSystem,
    val type: MepElementType,
    val diameterMm: Double,
    val material: String,
    val label: String,
    val existingId: String? = null,
    val createdAt: Long? = null
)

@Composable
fun ProjectMepScreen(
    dao: MaterialsDao,
    projectId: String,
    surveys: List<SiteSurveyEntity>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedRoomId by remember(projectId) { mutableStateOf<String?>(null) }
    val firstValidSurvey = surveys.firstOrNull { MepRoomBounds(it.lengthM, it.widthM, it.heightM).isValid }
    val survey = surveys.firstOrNull { it.id == selectedRoomId && MepRoomBounds(it.lengthM, it.widthM, it.heightM).isValid }
        ?: firstValidSurvey ?: surveys.firstOrNull()
    val elements by dao.observeMepElements(projectId, survey?.id.orEmpty()).collectAsState(initial = emptyList())
    LaunchedEffect(surveys) {
        if (surveys.none { it.id == selectedRoomId && MepRoomBounds(it.lengthM, it.widthM, it.heightM).isValid }) {
            selectedRoomId = firstValidSurvey?.id ?: surveys.firstOrNull()?.id
        }
    }
    var selectedSystem by remember(projectId) { mutableStateOf(MepSystem.COLD_WATER) }
    var visibleSystems by remember { mutableStateOf(MepSystem.entries.toSet()) }
    var showRoomShell by remember { mutableStateOf(true) }
    var editingElement by remember { mutableStateOf<MepElementEntity?>(null) }
    var showElementEditor by remember { mutableStateOf(false) }
    var drawingSpec by remember(projectId, survey?.id) { mutableStateOf<MepDrawingSpec?>(null) }
    var drawingPoints by remember(projectId, survey?.id) { mutableStateOf(emptyList<MepPoint>()) }
    var drawingElevation by remember(projectId, survey?.id) { mutableStateOf(0.45) }
    var drawingError by remember(projectId, survey?.id) { mutableStateOf<String?>(null) }
    var selectedElementId by remember(projectId, survey?.id) { mutableStateOf<String?>(null) }
    val selectedElement = elements.firstOrNull { it.id == selectedElementId }

    if (survey != null && !MepRoomBounds(survey.lengthM, survey.widthM, survey.heightM).isValid) {
        Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("مساحة الرسم ثلاثية الأبعاد", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "أبعاد هذا القياس غير صالحة للعرض ثلاثي الأبعاد. صحح الطول والعرض والارتفاع إلى أرقام موجبة لا تتجاوز 1000 متر من تبويب المعاينة.",
                    Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("مساحة الرسم ثلاثية الأبعاد", style = MaterialTheme.typography.titleLarge)
                Text("عرض هندسي محلي للمسارات والقطع. لا يحسب الضغط أو سلامة الكهرباء، ولا يطرح فتحات الأبواب والنوافذ من جدران المعاينة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (surveys.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("أضف قياسًا للمجلس أولًا", style = MaterialTheme.typography.titleMedium)
                    Text("لن ننشئ غرفة تجريبية أو نستخرج كميات دون أبعاد محفوظة. افتح تبويب المعاينة وسجل الطول والعرض والارتفاع.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                surveys.forEach { room ->
                    val roomIsValid = MepRoomBounds(room.lengthM, room.widthM, room.heightM).isValid
                    FilterChip(
                        selected = survey?.id == room.id,
                        enabled = roomIsValid,
                        onClick = { selectedRoomId = room.id; selectedElementId = null },
                        label = { Text(room.spaceName) }
                    )
                }
            }
            survey?.let { room ->
                Text("${room.spaceName}: ${fmt(room.lengthM)} × ${fmt(room.widthM)} × ${fmt(room.heightM)} م — النموذج مضبوط على القياس المحفوظ.", style = MaterialTheme.typography.bodySmall)

                MepScene(
                    modifier = Modifier.fillMaxWidth().weight(1.25f),
                    room = room,
                    elements = elements.filter { MepSystem.fromStored(it.system) in visibleSystems },
                    visibleSystems = visibleSystems,
                    showShell = showRoomShell,
                    drawingPoints = drawingPoints,
                    drawingType = drawingSpec?.type,
                    drawingSystem = drawingSpec?.system,
                    onPickRoomPoint = { position, surface ->
                        val scale = 6.0 / maxOf(room.lengthM, room.widthM, room.heightM, 1.0)
                        val rawPoint = MepPoint(
                            position.x.toDouble() / scale,
                            if (surface == "mep-floor") drawingElevation.coerceIn(0.0, room.heightM)
                            else position.y.toDouble() / scale,
                            position.z.toDouble() / scale
                        )
                        val point = MepGeometryCodec.snapToRoom(
                            rawPoint,
                            MepRoomBounds(room.lengthM, room.widthM, room.heightM),
                            MEP_SNAP_GRID_METRES
                        )
                        val maxPoints = when (drawingSpec?.type) {
                            MepElementType.ELBOW -> 3
                            MepElementType.TEE -> 4
                            MepElementType.VALVE, MepElementType.LIGHT_POINT,
                            MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> 1
                            else -> 16
                        }
                        if (point != null && drawingSpec != null && drawingPoints.size < maxPoints) drawingPoints = drawingPoints + point
                    },
                    onSelectElement = { selectedElementId = it }
                )

                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = showRoomShell, onClick = { showRoomShell = !showRoomShell }, label = { Text(if (showRoomShell) "إخفاء الجدران" else "إظهار الجدران") })
                        if (elements.size < MAX_MEP_ELEMENTS_PER_SURVEY) {
                            Button(onClick = { editingElement = null; showElementEditor = true }) { Text("＋ ارسم في المشهد 3D") }
                        } else {
                            Text("بلغت هذه الغرفة حد $MAX_MEP_ELEMENTS_PER_SURVEY عنصرًا.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    drawingSpec?.let { spec ->
                        val canSave = when (spec.type) {
                            MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN -> drawingPoints.size >= 2
                            MepElementType.ELBOW -> drawingPoints.size == 3
                            MepElementType.TEE -> drawingPoints.size == 4
                            else -> drawingPoints.size == 1
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ارسم ${spec.type.label} — ${spec.system.label}", style = MaterialTheme.typography.titleSmall)
                                val drawingHelp = when (spec.type) {
                                    MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN -> "ابدأ بنقطة ثم المس نقاط الانعطاف والنهاية. نقطتان حد أدنى؛ يمكنك الاستمرار حتى 16 نقطة."
                                    MepElementType.ELBOW -> "المس بداية الكوع، ثم نقطة الانحناء، ثم النهاية."
                                    MepElementType.TEE -> "المس مركز وصلة T أولًا، ثم المس نهايات الأفرع الثلاثة."
                                    else -> "المس سطح الأرض أو الجدار مرة واحدة لوضع القطعة."
                                }
                                Text(drawingHelp, style = MaterialTheme.typography.bodySmall)
                                Text("اسحب بإصبع لتدوير المشهد، وبإصبعين للتقريب والإبعاد.", style = MaterialTheme.typography.bodySmall)
                                Text("النقاط: ${drawingPoints.size} • الالتقاط على شبكة 5 سم", style = MaterialTheme.typography.bodySmall)
                                Text("ارتفاع النقرة على الأرض: ${fmt(drawingElevation)} م", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = drawingElevation.toFloat().coerceIn(0f, room.heightM.toFloat()),
                                    onValueChange = { drawingElevation = it.toDouble() },
                                    valueRange = 0f..room.heightM.toFloat().coerceAtLeast(0.05f)
                                )
                                drawingError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { drawingPoints = drawingPoints.dropLast(1); drawingError = null }, enabled = drawingPoints.isNotEmpty()) { Text("تراجع نقطة") }
                                    TextButton(onClick = { drawingSpec = null; drawingPoints = emptyList(); drawingError = null }) { Text("إلغاء") }
                                    Button(onClick = {
                                        val bounds = MepRoomBounds(room.lengthM, room.widthM, room.heightM)
                                        val problem = MepGeometryCodec.validate(spec.type, drawingPoints)
                                            ?: if (drawingPoints.any { !bounds.contains(it) }) "إحدى النقاط خارج حدود الغرفة." else null
                                        if (problem != null) {
                                            drawingError = problem
                                        } else {
                                            val points = drawingPoints
                                            scope.launch {
                                                val entity = MepElementEntity(
                                                    id = spec.existingId ?: UUID.randomUUID().toString(),
                                                    projectId = projectId,
                                                    surveyId = room.id,
                                                    system = spec.system.name,
                                                    elementType = spec.type.name,
                                                    geometryData = MepGeometryCodec.encode(points)!!,
                                                    nominalDiameterMm = spec.diameterMm,
                                                    material = spec.material,
                                                    label = spec.label,
                                                    createdAt = spec.createdAt ?: System.currentTimeMillis()
                                                )
                                                dao.saveMepElement(entity)
                                                selectedElementId = entity.id
                                                selectedSystem = spec.system
                                            }
                                            drawingSpec = null
                                            drawingPoints = emptyList()
                                            drawingError = null
                                        }
                                    }, enabled = canSave) { Text("حفظ العنصر") }
                                }
                            }
                        }
                    }

                    Text("الطبقات — اضغط لتشغيل أو إخفاء", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        MepSystem.entries.groupBy { it.discipline }.forEach { (discipline, systems) ->
                            Text(if (discipline == MepDiscipline.PLUMBING) "السباكة والصرف" else "الكهرباء والإنارة", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                systems.forEach { system ->
                                    FilterChip(
                                        selected = system in visibleSystems,
                                        onClick = { visibleSystems = if (system in visibleSystems) visibleSystems - system else visibleSystems + system },
                                        label = { Text("● ${system.label}", color = parseColor(system.colorHex)) }
                                    )
                                }
                            }
                        }
                    }

                    selectedElement?.let { item ->
                        val itemSystem = MepSystem.fromStored(item.system)
                        val type = MepElementType.fromStored(item.elementType)
                        val points = MepGeometryCodec.decode(item.geometryData).orEmpty()
                        val length = type?.let { MepGeometryCodec.lengthMeters(it, points) } ?: 0.0
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(item.label.ifBlank { type?.label ?: "عنصر" }, style = MaterialTheme.typography.titleMedium)
                                Text("${itemSystem?.label ?: item.system} • ${type?.label ?: item.elementType} • ${fmt(item.nominalDiameterMm)} مم • ${item.material}")
                                if (type?.isRun == true || type == MepElementType.ELBOW || type == MepElementType.TEE) Text("الطول الهندسي: ${fmt(length)} م")
                                Text("الكمية والتكلفة لم تُربطا بعد ببنود BOQ؛ الطول المعروض قراءة هندسية أولية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { editingElement = item; showElementEditor = true }) { Text("إعادة رسم في 3D") }
                                    TextButton(onClick = { scope.launch { dao.deleteMepElement(item.id, projectId) }; selectedElementId = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    } ?: Text("المسّ مسارًا أو قطعة في المشهد لعرض بياناتها. عدد العناصر: ${elements.size}.", style = MaterialTheme.typography.bodySmall)

                    Text("ألوان الكهرباء توضيحية: R/Y/B أحمر/أصفر/أزرق، المحايد أسود، والحماية أخضر. لا تعتمد اللون وحده في التوصيل؛ راجع إصدار OES-4 المعتمد ومهندسًا مختصًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("التمديدات الحالية محفوظة محليًا على هذا الجهاز. ربطها بالحصر وجدول التنفيذ (4D) يأتي بعد اعتماد حسابات الكميات والتحقق على الهاتف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (showElementEditor) {
                        MepElementEditor(
                            existing = editingElement,
                            initialSystem = editingElement?.let { MepSystem.fromStored(it.system) } ?: selectedSystem,
                            onDismiss = { showElementEditor = false; editingElement = null },
                            onSave = { system, type, diameter, material, label ->
                                drawingSpec = MepDrawingSpec(
                                    system = system,
                                    type = type,
                                    diameterMm = diameter,
                                    material = material,
                                    label = label,
                                    existingId = editingElement?.id,
                                    createdAt = editingElement?.createdAt
                                )
                                drawingPoints = emptyList()
                                drawingElevation = room.heightM * 0.35
                                drawingError = null
                                showElementEditor = false
                                editingElement = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MepScene(
    modifier: Modifier,
    room: SiteSurveyEntity,
    elements: List<MepElementEntity>,
    visibleSystems: Set<MepSystem>,
    showShell: Boolean,
    drawingPoints: List<MepPoint>,
    drawingType: MepElementType?,
    drawingSystem: MepSystem?,
    onPickRoomPoint: (Position, String) -> Unit,
    onSelectElement: (String?) -> Unit
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val nodes = rememberNodes()
    val previewNodes = rememberNodes()
    val viewScale = 6.0 / maxOf(room.lengthM, room.widthM, room.heightM, 1.0)
    val length = (room.lengthM * viewScale).toFloat()
    val width = (room.widthM * viewScale).toFloat()
    val height = (room.heightM * viewScale).toFloat()
    val center = Position(length / 2f, height / 2f, width / 2f)
    val colors = remember(materialLoader) {
        MepSystem.entries.associateWith { system -> materialLoader.createColorInstance(toSceneColor(system.colorHex)) }
    }
    val shellMaterial = remember(materialLoader) { materialLoader.createColorInstance(colorOf(0.72f, 0.68f, 0.58f, 0.24f)) }
    val floorMaterial = remember(materialLoader) { materialLoader.createColorInstance(colorOf(0.47f, 0.42f, 0.34f, 1f)) }
    val cameraNode = rememberCameraNode(engine) {
        position = Position(length * 1.35f, height * 1.2f, width * 1.65f)
        lookAt(center)
    }
    val cameraManipulator = rememberCameraManipulator(
        orbitHomePosition = Position(length * 1.35f, height * 1.2f, width * 1.65f),
        targetPosition = center
    )

    LaunchedEffect(room.id, length, width, height, elements, visibleSystems, showShell) {
        nodes.toList().forEach { it.destroy() }
        nodes.clear()
        nodes += roomShell(engine, room, viewScale.toFloat(), showShell, floorMaterial, shellMaterial)
        elements.forEach { element ->
            val system = MepSystem.fromStored(element.system) ?: return@forEach
            val type = MepElementType.fromStored(element.elementType) ?: return@forEach
            val points = MepGeometryCodec.decode(element.geometryData) ?: return@forEach
            if (system !in visibleSystems || MepGeometryCodec.validate(type, points) != null) return@forEach
            val material = colors[system] ?: return@forEach
            nodes += elementNodes(engine, element, type, points, material, viewScale.toFloat())
        }
    }

    LaunchedEffect(drawingPoints, drawingType, drawingSystem, viewScale) {
        previewNodes.toList().forEach { it.destroy() }
        previewNodes.clear()
        val type = drawingType
        val material = drawingSystem?.let { colors[it] }
        if (type != null && material != null && drawingPoints.isNotEmpty()) {
            previewNodes += drawingPreviewNodes(engine, drawingPoints, type, material, viewScale.toFloat())
        }
    }

    Scene(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        cameraManipulator = cameraManipulator,
        childNodes = nodes + previewNodes,
        onTouchEvent = { event, hitResult ->
            if (event.action == MotionEvent.ACTION_UP && hitResult != null) {
                val name = hitResult.node.name.orEmpty()
                if (name == "mep-floor" || name == "mep-wall") {
                    if (drawingType != null) onPickRoomPoint(hitResult.worldPosition, name)
                    else onSelectElement(null)
                } else {
                    onSelectElement(name.takeUnless { it.isBlank() || it == "mep-room" })
                }
            }
            false
        }
    )
}

private fun roomShell(
    engine: com.google.android.filament.Engine,
    room: SiteSurveyEntity,
    scale: Float,
    visible: Boolean,
    floorMaterial: com.google.android.filament.MaterialInstance,
    wallMaterial: com.google.android.filament.MaterialInstance
): List<io.github.sceneview.node.Node> {
    val l = (room.lengthM * scale).toFloat()
    val w = (room.widthM * scale).toFloat()
    val h = (room.heightM * scale).toFloat()
    val result = mutableListOf<io.github.sceneview.node.Node>()
    result += CubeNode(engine, size = Size(l, 0.05f, w), center = Position(l / 2f, -0.03f, w / 2f), materialInstance = floorMaterial).apply {
        name = "mep-floor"
        isTouchable = true
    }
    if (visible) {
        val t = 0.035f
        result += CubeNode(engine, size = Size(l, h, t), center = Position(l / 2f, h / 2f, 0f), materialInstance = wallMaterial).apply { name = "mep-wall"; isTouchable = true }
        result += CubeNode(engine, size = Size(l, h, t), center = Position(l / 2f, h / 2f, w), materialInstance = wallMaterial).apply { name = "mep-wall"; isTouchable = true }
        result += CubeNode(engine, size = Size(t, h, w), center = Position(0f, h / 2f, w / 2f), materialInstance = wallMaterial).apply { name = "mep-wall"; isTouchable = true }
        result += CubeNode(engine, size = Size(t, h, w), center = Position(l, h / 2f, w / 2f), materialInstance = wallMaterial).apply { name = "mep-wall"; isTouchable = true }
    }
    return result
}

private fun drawingPreviewNodes(
    engine: com.google.android.filament.Engine,
    points: List<MepPoint>,
    type: MepElementType,
    material: com.google.android.filament.MaterialInstance,
    scale: Float
): List<io.github.sceneview.node.Node> {
    val output = mutableListOf<io.github.sceneview.node.Node>()
    val scaled = points.map { MepPoint(it.x * scale, it.y * scale, it.z * scale) }
    val segments = when (type) {
        MepElementType.TEE -> scaled.drop(1).map { scaled.first() to it }
        MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN, MepElementType.ELBOW -> scaled.zipWithNext()
        else -> emptyList()
    }
    segments.forEach { (start, end) ->
        val length = MepGeometryCodec.distance(start, end).toFloat()
        if (length > 0.002f) {
            val midpoint = Position(
                ((start.x + end.x) / 2).toFloat(),
                ((start.y + end.y) / 2).toFloat(),
                ((start.z + end.z) / 2).toFloat()
            )
            output += CylinderNode(engine, radius = 0.018f, height = length, materialInstance = material).apply {
                name = "mep-preview"
                isTouchable = false
                position = midpoint
                alignLocalYWith(this, start, end)
            }
        }
    }
    scaled.forEach { point ->
        output += SphereNode(
            engine,
            radius = 0.032f,
            center = Position(point.x.toFloat(), point.y.toFloat(), point.z.toFloat()),
            materialInstance = material
        ).apply {
            name = "mep-preview"
            isTouchable = false
        }
    }
    return output
}

private fun elementNodes(
    engine: com.google.android.filament.Engine,
    entity: MepElementEntity,
    type: MepElementType,
    points: List<MepPoint>,
    material: com.google.android.filament.MaterialInstance,
    scale: Float
): List<io.github.sceneview.node.Node> {
    val radius = ((entity.nominalDiameterMm / 2000.0) * scale).toFloat().coerceIn(0.009f, 0.18f)
    val output = mutableListOf<io.github.sceneview.node.Node>()
    fun addSegment(start: MepPoint, end: MepPoint) {
        val scaledStart = MepPoint(start.x * scale, start.y * scale, start.z * scale)
        val scaledEnd = MepPoint(end.x * scale, end.y * scale, end.z * scale)
        val length = MepGeometryCodec.distance(scaledStart, scaledEnd).toFloat()
        if (length < 0.002f) return
        val midpoint = Position(((scaledStart.x + scaledEnd.x) / 2).toFloat(), ((scaledStart.y + scaledEnd.y) / 2).toFloat(), ((scaledStart.z + scaledEnd.z) / 2).toFloat())
        output += CylinderNode(engine, radius = radius, height = length, materialInstance = material).apply {
            name = entity.id
            position = midpoint
            alignLocalYWith(this, scaledStart, scaledEnd)
        }
    }
    when (type) {
        MepElementType.PIPE_RUN, MepElementType.CONDUIT_RUN, MepElementType.CABLE_RUN, MepElementType.ELBOW ->
            points.zipWithNext().forEach { (a, b) -> addSegment(a, b) }
        MepElementType.TEE -> points.drop(1).forEach { branch -> addSegment(points.first(), branch) }
        MepElementType.VALVE, MepElementType.LIGHT_POINT, MepElementType.SWITCH_POINT, MepElementType.SOCKET_POINT -> {
            val p = points.first()
            output += SphereNode(engine, radius = radius * 2.2f, center = Position((p.x * scale).toFloat(), (p.y * scale).toFloat(), (p.z * scale).toFloat()), materialInstance = material).apply { name = entity.id }
        }
    }
    return output
}

private fun alignLocalYWith(node: CylinderNode, start: MepPoint, end: MepPoint) {
    val rotation = MepGeometryCodec.localYToSegmentRotation(start, end) ?: return
    node.rotation = Rotation(rotation.xDegrees.toFloat(), rotation.yDegrees.toFloat(), rotation.zDegrees.toFloat())
}

@Composable
private fun MepElementEditor(
    existing: MepElementEntity?,
    initialSystem: MepSystem,
    onDismiss: () -> Unit,
    onSave: (MepSystem, MepElementType, Double, String, String) -> Unit
) {
    var system by remember(existing?.id) { mutableStateOf(initialSystem) }
    val choices = MepElementType.allowedFor(system)
    var type by remember(existing?.id) {
        mutableStateOf(existing?.let { MepElementType.fromStored(it.elementType) }?.takeIf { it in choices } ?: choices.first())
    }
    var diameter by remember(existing?.id) { mutableStateOf(existing?.nominalDiameterMm?.let(::fmt) ?: if (system.discipline == MepDiscipline.PLUMBING) "25" else "20") }
    var material by remember(existing?.id) { mutableStateOf(existing?.material ?: if (system.discipline == MepDiscipline.PLUMBING) "PPR" else "PVC / نحاس حسب الموصل") }
    var label by remember(existing?.id) { mutableStateOf(existing?.label ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة عنصر MEP" else "تعديل عنصر MEP") },
        text = {
            Column(Modifier.fillMaxWidth().height(500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الخدمة", style = MaterialTheme.typography.labelLarge)
                SimpleMenu(value = system.label, options = MepSystem.entries.map { it to it.label }, onSelect = {
                    system = it
                    if (type !in MepElementType.allowedFor(it)) type = MepElementType.allowedFor(it).first()
                })
                Text("نوع العنصر", style = MaterialTheme.typography.labelLarge)
                SimpleMenu(value = type.label, options = choices.map { it to it.label }, onSelect = { type = it })
                Text("بعد اختيار المواصفات، ستضع المسار أو القطعة مباشرة على أرضية/جدار المشهد ثلاثي الأبعاد.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(diameter, { diameter = it }, Modifier.fillMaxWidth(), label = { Text("القطر الاسمي (مم)") }, singleLine = true)
                OutlinedTextField(material, { material = it }, Modifier.fillMaxWidth(), label = { Text("المادة / نوع الموصل") }, singleLine = true)
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), label = { Text("وسم اختياري") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("ألوان الخدمات للتوضيح فقط؛ اعتمد الاسم والمخطط ومراجعة المختص.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                val nominal = MepGeometryCodec.parseDecimal(diameter)
                val problem = when {
                    type !in MepElementType.allowedFor(system) -> "نوع القطعة لا يتبع الخدمة المختارة."
                    nominal == null || !nominal.isFinite() || nominal <= 0.0 || nominal > 1000.0 -> "أدخل قطرًا اسميًا موجبًا لا يتجاوز 1000 مم."
                    material.isBlank() -> "أدخل المادة أو نوع الموصل."
                    else -> null
                }
                if (problem != null) error = problem else onSave(system, type, nominal!!, material.trim(), label.trim())
            }) { Text("الانتقال إلى الرسم 3D") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun <T : Any> SimpleMenu(value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private fun toSceneColor(hex: String): io.github.sceneview.math.Color {
    val rgb = parseColor(hex)
    return colorOf(rgb.red, rgb.green, rgb.blue, rgb.alpha)
}

private fun parseColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
private fun fmt(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
