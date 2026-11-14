package com.majlisengineer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majlisengineer.data.MaterialCategoryEntity
import com.majlisengineer.data.MaterialEntity
import com.majlisengineer.data.MaterialSearchNormalizer
import com.majlisengineer.data.MaterialSupplierOfferEntity
import com.majlisengineer.data.MaterialsDao
import com.majlisengineer.data.SupplierEntity
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private enum class MaterialManagementPage(val title: String) { Catalog("المواد"), Suppliers("الموردون") }

@Composable
fun MaterialManagementScreen(dao: MaterialsDao) {
    val categories by dao.observeCategories().collectAsState(initial = emptyList())
    val materials by dao.observeMaterials().collectAsState(initial = emptyList())
    val suppliers by dao.observeSuppliers().collectAsState(initial = emptyList())
    val offers by dao.observeSupplierOffers().collectAsState(initial = emptyList())
    var page by remember { mutableStateOf(MaterialManagementPage.Catalog) }
    var search by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var editingSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var editingBasePrice by remember { mutableStateOf<MaterialEntity?>(null) }
    var offerMaterial by remember { mutableStateOf<MaterialEntity?>(null) }
    val scope = rememberCoroutineScope()
    val normalizedSearch = remember(search) { MaterialSearchNormalizer.normalize(search) }
    val filtered = materials.filter { material ->
        (categoryId == null || material.categoryId == categoryId) &&
            (normalizedSearch.isBlank() || material.normalizedSearchText.contains(normalizedSearch))
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("المواد والموردون", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialManagementPage.values().forEach { item ->
                FilterChip(selected = page == item, onClick = { page = item }, label = { Text(item.title) })
            }
        }
        when (page) {
            MaterialManagementPage.Catalog -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("كتالوج المواد", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { showMaterialDialog = true }) { Text("＋ صنف آخر") }
                }
                Text("${materials.size} صنفًا جاهزًا على الجهاز. الأسماء عامة؛ الأسعار والعلامات التجارية لا تُفترض.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("السعر المسجل للمادة مستقل عن سعر المشروع. الأصناف بلا سعر تظهر غير محددة ولا تعني أنها مجانية.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("بحث بالعربية أو الإنجليزية") }, singleLine = true)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = categoryId == null, onClick = { categoryId = null }, label = { Text("الكل") })
                    categories.forEach { category ->
                        FilterChip(selected = categoryId == category.id, onClick = { categoryId = if (categoryId == category.id) null else category.id }, label = { Text(category.nameAr) })
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (filtered.isEmpty()) item { EmptyManagementCard("لا توجد مواد مطابقة", "أضف صنفًا غير موجود أو غيّر عبارة البحث.") }
                    items(filtered, key = { it.id }) { material ->
                        MaterialPriceCard(
                            material = material,
                            categoryName = categories.firstOrNull { it.id == material.categoryId }?.nameAr.orEmpty(),
                            offers = offers.filter { it.materialId == material.id }.sortedWith(compareBy({ it.unit }, { it.unitPriceOmr })),
                            suppliers = suppliers,
                            onEditBase = { editingBasePrice = material },
                            onAddOffer = { offerMaterial = material }
                        )
                    }
                }
            }
            MaterialManagementPage.Suppliers -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("دليل الموردين", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { editingSupplier = null; showSupplierDialog = true }) { Text("＋ مورد") }
                }
                Text("أضف بيانات التواصل وقارن عروض الموردين المسجلة على كل مادة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (suppliers.isEmpty()) EmptyManagementCard("لا يوجد موردون", "أضف موردًا لتسجيل عروض أسعاره.")
                    suppliers.forEach { supplier ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(supplier.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                if (supplier.contactName.isNotBlank()) Text("جهة الاتصال: ${supplier.contactName}", fontSize = 12.sp)
                                if (supplier.phone.isNotBlank()) Text("الهاتف: ${supplier.phone}", fontSize = 12.sp)
                                if (supplier.email.isNotBlank()) Text("البريد: ${supplier.email}", fontSize = 12.sp)
                                if (supplier.location.isNotBlank()) Text("الموقع: ${supplier.location}", fontSize = 12.sp)
                                if (supplier.notes.isNotBlank()) Text(supplier.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { editingSupplier = supplier; showSupplierDialog = true }) { Text("تعديل بيانات المورد") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMaterialDialog) MaterialFormDialog(categories, onDismiss = { showMaterialDialog = false }) { material ->
        scope.launch { dao.insertMaterials(listOf(material)) }
        showMaterialDialog = false
    }
    editingBasePrice?.let { material ->
        MaterialBasePriceDialog(material, onDismiss = { editingBasePrice = null }) { updated ->
            scope.launch { dao.updateMaterial(updated) }
            editingBasePrice = null
        }
    }
    if (showSupplierDialog) SupplierFormDialog(editingSupplier, onDismiss = { showSupplierDialog = false; editingSupplier = null }) { supplier ->
        scope.launch { if (editingSupplier == null) dao.insertSupplier(supplier) else dao.updateSupplier(supplier) }
        showSupplierDialog = false
        editingSupplier = null
    }
    offerMaterial?.let { material ->
        SupplierOfferDialog(material, suppliers, onDismiss = { offerMaterial = null }) { offer ->
            scope.launch { dao.insertSupplierOffer(offer) }
            offerMaterial = null
        }
    }
}

@Composable
private fun MaterialPriceCard(
    material: MaterialEntity,
    categoryName: String,
    offers: List<MaterialSupplierOfferEntity>,
    suppliers: List<SupplierEntity>,
    onEditBase: () -> Unit,
    onAddOffer: () -> Unit
) {
    val basePriceLabel = if (material.basePriceUpdatedAt > 0L) formatMaterialOmr(material.baseUnitPriceOmr) else "غير محدد"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(material.nameAr, style = MaterialTheme.typography.titleMedium)
            if (material.nameEn.isNotBlank()) Text(material.nameEn, fontSize = 12.sp)
            Text(categoryName, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            if (material.brand.isNotBlank()) Text("الماركة: ${material.brand}", fontSize = 12.sp)
            Text("السعر الأساسي: $basePriceLabel / ${material.baseUnit}", fontSize = 13.sp)
            Text(
                "آخر تحديث للسعر الأساسي: ${if (material.basePriceUpdatedAt > 0) java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(material.basePriceUpdatedAt)) else "لا يوجد سعر مسجل"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (material.shortNote.isNotBlank()) Text(material.shortNote, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onEditBase) { Text("تعديل بيانات السعر") }
                TextButton(onClick = onAddOffer, enabled = suppliers.isNotEmpty()) { Text("＋ عرض مورد") }
            }
            if (offers.isNotEmpty()) {
                HorizontalDivider()
                Text("العروض مرتبة حسب وحدة البيع ثم السعر؛ لا تقارن أسعار وحدات مختلفة مباشرة.", style = MaterialTheme.typography.labelLarge)
                offers.forEach { offer ->
                    val supplier = suppliers.firstOrNull { it.id == offer.supplierId }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(supplier?.name ?: "مورد غير موجود", fontSize = 12.sp)
                            Text(listOf(offer.brand, offer.unit, "تحديث ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(offer.updatedAt))}").filter { it.isNotBlank() }.joinToString(" • "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${formatMaterialOmr(offer.unitPriceOmr)} / ${offer.unit}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialFormDialog(categories: List<MaterialCategoryEntity>, onDismiss: () -> Unit, onSave: (MaterialEntity) -> Unit) {
    var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("قطعة") }
    var brand by remember { mutableStateOf("") }
    var basePrice by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val parsedPrice = if (basePrice.isBlank()) null else basePrice.toDoubleOrNull()
    val valid = nameAr.isNotBlank() && categoryId.isNotBlank() && unit.isNotBlank() &&
        (basePrice.isBlank() || ((parsedPrice ?: -1.0) >= 0 && parsedPrice?.isFinite() == true))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مادة إلى الكتالوج") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(categories.firstOrNull { it.id == categoryId }?.nameAr ?: "اختر التصنيف") }
                    DropdownMenu(expanded, { expanded = false }) {
                        categories.forEach { category -> DropdownMenuItem(text = { Text(category.nameAr) }, onClick = { categoryId = category.id; expanded = false }) }
                    }
                }
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم المادة بالعربية *") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم بالإنجليزية") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("وحدة القياس / البيع *") }, singleLine = true)
                OutlinedTextField(brand, { brand = it }, label = { Text("الماركة") }, singleLine = true)
                OutlinedTextField(basePrice, { basePrice = it }, label = { Text("السعر الأساسي للوحدة ر.ع (اختياري)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة تعريفية") })
                OutlinedTextField(aliases, { aliases = it }, label = { Text("أسماء بديلة للبحث") })
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            val normalized = MaterialSearchNormalizer.normalize("$nameAr $nameEn $aliases")
            onSave(MaterialEntity(UUID.randomUUID().toString(), categoryId, nameAr.trim(), nameEn.trim(), shortNote = note.trim(), aliases = aliases.trim(), normalizedSearchText = normalized, baseUnit = unit.trim(), brand = brand.trim(), baseUnitPriceOmr = parsedPrice ?: 0.0, basePriceUpdatedAt = if (parsedPrice == null) 0L else System.currentTimeMillis()))
        }) { Text("حفظ المادة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun MaterialBasePriceDialog(material: MaterialEntity, onDismiss: () -> Unit, onSave: (MaterialEntity) -> Unit) {
    var brand by remember(material.id) { mutableStateOf(material.brand) }
    var price by remember(material.id) { mutableStateOf(if (material.basePriceUpdatedAt > 0L) material.baseUnitPriceOmr.toString() else "") }
    val parsed = if (price.isBlank()) null else price.toDoubleOrNull()
    val valid = price.isBlank() || ((parsed ?: -1.0) >= 0 && parsed?.isFinite() == true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل السعر الأساسي — ${material.nameAr}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("وحدة البيع الثابتة: ${material.baseUnit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(brand, { brand = it }, label = { Text("الماركة") }, singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("السعر الأساسي للوحدة ر.ع (اتركه فارغًا إن لم يُسعّر)") }, singleLine = true)
            Text("يُحدّث سجل هذه المادة فقط. سعر أي مشروع محفوظ كاستثناء لا يتغير.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(material.copy(brand = brand.trim(), baseUnitPriceOmr = parsed ?: 0.0, basePriceUpdatedAt = if (parsed == null) 0L else System.currentTimeMillis())) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SupplierFormDialog(existing: SupplierEntity?, onDismiss: () -> Unit, onSave: (SupplierEntity) -> Unit) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var contact by remember(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var email by remember(existing?.id) { mutableStateOf(existing?.email.orEmpty()) }
    var location by remember(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة مورد" else "تعديل المورد") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("اسم المورد *") }, singleLine = true)
            OutlinedTextField(contact, { contact = it }, label = { Text("جهة الاتصال") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, singleLine = true)
            OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true)
            OutlinedTextField(location, { location = it }, label = { Text("الموقع") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(SupplierEntity(existing?.id ?: UUID.randomUUID().toString(), name.trim(), contact.trim(), phone.trim(), email.trim(), location.trim(), notes.trim(), existing?.createdAt ?: System.currentTimeMillis())) }) { Text("حفظ المورد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SupplierOfferDialog(material: MaterialEntity, suppliers: List<SupplierEntity>, onDismiss: () -> Unit, onSave: (MaterialSupplierOfferEntity) -> Unit) {
    var supplierId by remember(suppliers) { mutableStateOf(suppliers.firstOrNull()?.id.orEmpty()) }
    var unit by remember(material.id) { mutableStateOf(material.baseUnit) }
    var brand by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val parsed = price.toDoubleOrNull()
    val valid = suppliers.any { it.id == supplierId } && unit.isNotBlank() && (parsed ?: -1.0) >= 0 && parsed?.isFinite() == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عرض مورد — ${material.nameAr}") },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(suppliers.firstOrNull { it.id == supplierId }?.name ?: "اختر المورد") }
                DropdownMenu(expanded, { expanded = false }) {
                    suppliers.forEach { supplier -> DropdownMenuItem(text = { Text(supplier.name) }, onClick = { supplierId = supplier.id; expanded = false }) }
                }
            }
            OutlinedTextField(unit, { unit = it }, label = { Text("وحدة العرض") }, singleLine = true)
            OutlinedTextField(brand, { brand = it }, label = { Text("الماركة") }, singleLine = true)
            OutlinedTextField(price, { price = it }, label = { Text("السعر للوحدة ر.ع *") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات العرض / صلاحية السعر") })
            Text("يُحفظ العرض بتاريخ اليوم للمقارنة؛ لا يغيّر السعر الأساسي للمادة أو أسعار المشاريع.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(MaterialSupplierOfferEntity(UUID.randomUUID().toString(), material.id, supplierId, brand.trim(), unit.trim(), parsed!!, notes.trim())) }) { Text("حفظ العرض") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EmptyManagementCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMaterialOmr(value: Double): String = String.format(Locale.US, "%.3f ر.ع", value)
