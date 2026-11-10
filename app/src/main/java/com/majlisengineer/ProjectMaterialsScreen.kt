package com.majlisengineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majlisengineer.data.MaterialEntity
import com.majlisengineer.data.MaterialsDao
import com.majlisengineer.data.ProjectMaterialEntity
import kotlinx.coroutines.launch
import android.widget.Toast
import java.util.Locale
import java.util.UUID

@Composable
fun ProjectMaterialsScreen(dao: MaterialsDao, projectId: String, modifier: Modifier = Modifier) {
    val materials by dao.observeMaterials().collectAsState(initial = emptyList())
    val items by dao.observeProjectMaterials(projectId).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProjectMaterialEntity?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val materialById = remember(materials) { materials.associateBy { it.id } }
    val trackedMaterialIds = remember(items) { items.map { it.materialId }.toSet() }
    val valued = items.mapNotNull { item ->
        materialById[item.materialId]?.let { material ->
            val price = item.projectUnitPriceOverrideOmr ?: material.baseUnitPriceOmr
            Triple(item, material, price)
        }
    }
    val plannedCost = valued.sumOf { (item, _, price) -> item.requiredQuantity * price }
    val consumedCost = valued.sumOf { (item, _, price) -> item.usedQuantity * price }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("مواد المشروع والمخزون", style = MaterialTheme.typography.headlineSmall)
        Text("السعر الخاص بالمشروع اختياري؛ تركه فارغًا يستخدم السعر الأساسي للكتالوج دون تغييره.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectInventoryMetricCard("مواد مسجلة", items.size.toString(), Modifier.weight(1f))
            ProjectInventoryMetricCard("تكلفة المطلوب", formatInventoryOmr(plannedCost), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectInventoryMetricCard("قيمة المستخدم", formatInventoryOmr(consumedCost), Modifier.weight(1f))
            ProjectInventoryMetricCard("المتبقي المطلوب", formatInventoryQty(valued.sumOf { it.first.remainingQuantity }), Modifier.weight(1f))
        }
        Button(onClick = { editing = null; showAdd = true }, enabled = materials.any { it.id !in trackedMaterialIds }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة مادة للمشروع") }
        if (materials.isEmpty()) EmptyInventoryCard("كتالوج المواد فارغ", "أضف المواد أولًا في تبويب المواد قبل تسجيل احتياج المشروع.")
        if (items.isEmpty() && materials.isNotEmpty()) EmptyInventoryCard("لم تسجل مواد للمشروع", "أضف المواد المطلوبة لمتابعة الاستخدام والمتبقي.")
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            valued.forEach { (item, material, effectivePrice) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(material.nameAr, style = MaterialTheme.typography.titleMedium)
                        Text("المطلوب ${formatInventoryQty(item.requiredQuantity)} ${item.unitSnapshot} • المستخدم ${formatInventoryQty(item.usedQuantity)} ${item.unitSnapshot}", fontSize = 12.sp)
                        val remaining = item.remainingQuantity
                        Text(if (remaining >= 0) "المتبقي ${formatInventoryQty(remaining)} ${item.unitSnapshot}" else "عجز ${formatInventoryQty(-remaining)} ${item.unitSnapshot}", fontSize = 12.sp, color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text("سعر الأساس ${formatInventoryOmr(material.baseUnitPriceOmr)} • سعر المشروع ${formatInventoryOmr(effectivePrice)} / ${item.unitSnapshot}", fontSize = 12.sp)
                        if (item.projectUnitPriceOverrideOmr != null) Text("سعر خاص بهذا المشروع؛ لا يغيّر السعر الأساسي.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("ميزانية المادة ${formatInventoryOmr(item.requiredQuantity * effectivePrice)} • المستخدم ${formatInventoryOmr(item.usedQuantity * effectivePrice)}", fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = item; showAdd = true }) { Text("تعديل الكمية أو السعر") }
                            TextButton(onClick = { scope.launch { dao.deleteProjectMaterial(item.id) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) ProjectMaterialDialog(
        projectId = projectId,
        materials = materials,
        existing = editing,
        alreadyTrackedMaterialIds = trackedMaterialIds,
        onDismiss = { showAdd = false; editing = null },
        onSave = { item ->
            scope.launch {
                if (editing == null) {
                    val insertedRowId = dao.insertProjectMaterial(item)
                    if (insertedRowId == -1L) Toast.makeText(context, "هذه المادة مسجلة لهذا المشروع بالفعل؛ لم تُستبدل بياناتها.", Toast.LENGTH_LONG).show()
                } else {
                    dao.updateProjectMaterial(item)
                }
            }
            showAdd = false
            editing = null
        }
    )
}

@Composable
private fun ProjectMaterialDialog(
    projectId: String,
    materials: List<MaterialEntity>,
    existing: ProjectMaterialEntity?,
    alreadyTrackedMaterialIds: Set<String>,
    onDismiss: () -> Unit,
    onSave: (ProjectMaterialEntity) -> Unit
) {
    val selectableMaterials = materials.filter { existing?.materialId == it.id || it.id !in alreadyTrackedMaterialIds }
    var materialId by remember(existing?.id, selectableMaterials) { mutableStateOf(existing?.materialId ?: selectableMaterials.firstOrNull()?.id.orEmpty()) }
    var required by remember(existing?.id) { mutableStateOf(existing?.requiredQuantity?.toString().orEmpty()) }
    var used by remember(existing?.id) { mutableStateOf(existing?.usedQuantity?.toString() ?: "0") }
    var overridePrice by remember(existing?.id) { mutableStateOf(existing?.projectUnitPriceOverrideOmr?.toString().orEmpty()) }
    var menuExpanded by remember { mutableStateOf(false) }
    val parsedRequired = required.toDoubleOrNull()
    val parsedUsed = used.toDoubleOrNull()
    val parsedOverride = if (overridePrice.isBlank()) null else overridePrice.toDoubleOrNull()
    val selected = materials.firstOrNull { it.id == materialId }
    val effectivePrice = parsedOverride ?: selected?.baseUnitPriceOmr ?: 0.0
    val valid = selected != null && (parsedRequired ?: 0.0) > 0.0 && (parsedUsed ?: -1.0) >= 0.0 &&
        (overridePrice.isBlank() || (parsedOverride ?: -1.0) >= 0.0) &&
        listOf(parsedRequired, parsedUsed).all { it?.isFinite() == true } &&
        (overridePrice.isBlank() || parsedOverride?.isFinite() == true) && effectivePrice.isFinite() &&
        ((parsedRequired ?: 0.0) * effectivePrice).isFinite() && ((parsedUsed ?: 0.0) * effectivePrice).isFinite()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة مادة للمشروع" else "تعديل مادة المشروع") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(enabled = existing == null, onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected?.nameAr ?: "اختر المادة") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    selectableMaterials.forEach { material ->
                        DropdownMenuItem(text = { Text("${material.nameAr} • ${material.baseUnit}") }, onClick = { materialId = material.id; menuExpanded = false })
                    }
                }
            }
            selected?.let { Text("السعر الأساسي الحالي: ${formatInventoryOmr(it.baseUnitPriceOmr)} / ${it.baseUnit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(required, { required = it }, label = { Text("الكمية المطلوبة *") }, singleLine = true)
            OutlinedTextField(used, { used = it }, label = { Text("الكمية المستخدمة") }, singleLine = true)
            OutlinedTextField(overridePrice, { overridePrice = it }, label = { Text("سعر خاص للمشروع / الوحدة ر.ع") }, singleLine = true)
            Text("اترك سعر المشروع فارغًا لاستخدام سعر الأساس. يمكن تسجيل استخدام يتجاوز المطلوب لإظهار العجز.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(
                ProjectMaterialEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    materialId = materialId,
                    unitSnapshot = existing?.unitSnapshot ?: selected?.baseUnit.orEmpty(),
                    requiredQuantity = parsedRequired!!,
                    usedQuantity = parsedUsed!!,
                    projectUnitPriceOverrideOmr = parsedOverride,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }) { Text(if (existing == null) "حفظ" else "حفظ التعديلات") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EmptyInventoryCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectInventoryMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatInventoryOmr(value: Double): String = String.format(Locale.US, "%.3f ر.ع", value)
private fun formatInventoryQty(value: Double): String = String.format(Locale.US, "%.3f", value)
