package com.majlisengineer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majlisengineer.data.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = (application as MajlisEngineerApp).database.materialsDao()
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { MaterialsHomeScreen(dao) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialsHomeScreen(dao: MaterialsDao) {
    val categories by dao.observeCategories().collectAsState(initial = emptyList())
    val materials by dao.observeMaterials().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val normalized = remember(search) { MaterialSearchNormalizer.normalize(search) }
    val visibleMaterials = materials.filter {
        (selectedCategory == null || it.categoryId == selectedCategory) &&
        (normalized.isBlank() || it.normalizedSearchText.contains(normalized))
    }

    Scaffold(
        topBar = { TopAppBar(title = { Column(horizontalAlignment = Alignment.End) {
            Text("مهندس المجلس", fontWeight = FontWeight.Bold)
            Text("مكتبة المواد الهندسية", fontSize = 12.sp)
        } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAddDialog = true }) { Text("+", fontSize = 24.sp) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("المواد والتشطيبات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("كتالوج محلي يعمل دون اتصال بالإنترنت", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(),
                    label = { Text("ابحث عن مادة") }, placeholder = { Text("مثال: إنارة، رخام، أنابيب") }, singleLine = true)
            }
            item {
                Text("التصنيفات (${categories.size})", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedCategory == null, { selectedCategory = null }, label = { Text("الكل") })
                    categories.forEach { category ->
                        FilterChip(selectedCategory == category.id,
                            { selectedCategory = if (selectedCategory == category.id) null else category.id },
                            label = { Text(category.nameAr) })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المواد", fontWeight = FontWeight.Bold)
                    Text("${visibleMaterials.size} مادة", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (visibleMaterials.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (materials.isEmpty()) "المكتبة جاهزة لإضافة المواد" else "لا توجد نتائج مطابقة")
                            Text("استخدم زر + لإضافة مادة إلى الكتالوج المحلي",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(visibleMaterials, key = { it.id }) { material ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(material.nameAr, fontWeight = FontWeight.SemiBold)
                            if (material.nameEn.isNotBlank()) Text(material.nameEn, fontSize = 12.sp)
                            Text(categories.firstOrNull { it.id == material.categoryId }?.nameAr.orEmpty(),
                                color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            if (material.shortNote.isNotBlank()) Text(material.shortNote, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMaterialDialog(categories, { showAddDialog = false }) { categoryId, nameAr, nameEn, note, aliases ->
            val searchable = MaterialSearchNormalizer.normalize("$nameAr $nameEn $aliases")
            scope.launch {
                dao.insertMaterials(listOf(MaterialEntity(
                    id = UUID.randomUUID().toString(), categoryId = categoryId,
                    nameAr = nameAr.trim(), nameEn = nameEn.trim(), shortNote = note.trim(),
                    aliases = aliases.trim(), normalizedSearchText = searchable
                )))
            }
            showAddDialog = false
        }
    }
}

@Composable
private fun AddMaterialDialog(
    categories: List<MaterialCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var categoryId by remember(categories) { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مادة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(categories.firstOrNull { it.id == categoryId }?.nameAr ?: "اختر التصنيف")
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.nameAr) }, onClick = {
                                categoryId = category.id; expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم المادة بالعربية *") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم بالإنجليزية") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة تعريفية") })
                OutlinedTextField(aliases, { aliases = it }, label = { Text("أسماء بديلة للبحث") })
            }
        },
        confirmButton = {
            TextButton(enabled = nameAr.isNotBlank() && categoryId.isNotBlank(),
                onClick = { onSave(categoryId, nameAr, nameEn, note, aliases) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
