package com.majlisengineer

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.majlisengineer.data.*
import com.majlisengineer.cad.ProjectCadScreen
import com.majlisengineer.costs.CostCalculator
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = (application as MajlisEngineerApp).database.materialsDao()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF285C50))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { MajlisEngineerRoot(dao) }
                }
            }
        }
    }
}

private enum class MainDestination(val label: String) {
    Dashboard("الرئيسية"), Projects("المشاريع"), Materials("المواد")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MajlisEngineerRoot(dao: MaterialsDao) {
    var destination by remember { mutableStateOf(MainDestination.Dashboard) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }
    val projects by dao.observeProjects().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (activeProjectId == null) "مهندس المجلس" else "ملف المشروع", fontWeight = FontWeight.Bold)
                        Text("إدارة أعمال المجالس والتشطيبات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (activeProjectId != null) {
                        TextButton(onClick = { activeProjectId = null }) { Text("رجوع") }
                    }
                }
            )
        },
        bottomBar = {
            if (activeProjectId == null) {
                NavigationBar {
                    MainDestination.values().forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = { Text(when (item) { MainDestination.Dashboard -> "⌂"; MainDestination.Projects -> "▣"; MainDestination.Materials -> "▤" }, fontSize = 20.sp) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (activeProjectId != null) {
                ProjectDetailScreen(dao, activeProjectId!!) { activeProjectId = null }
            } else {
                when (destination) {
                    MainDestination.Dashboard -> DashboardScreen(
                        projects = projects,
                        onOpenProject = { activeProjectId = it },
                        onProjects = { destination = MainDestination.Projects }
                    )
                    MainDestination.Projects -> ProjectsScreen(dao, projects, onOpenProject = { activeProjectId = it })
                    MainDestination.Materials -> MaterialsScreen(dao)
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    projects: List<ProjectEntity>,
    onOpenProject: (String) -> Unit,
    onProjects: () -> Unit
) {
    val active = projects.count { it.status != "مكتمل" }
    val complete = projects.count { it.status == "مكتمل" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("لوحة متابعة الأعمال", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("كل بياناتك محفوظة على الجهاز وتعمل دون اتصال بالإنترنت.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("إجمالي المشاريع", projects.size.toString(), Modifier.weight(1f))
            MetricCard("قيد العمل", active.toString(), Modifier.weight(1f))
            MetricCard("مكتملة", complete.toString(), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("أحدث المشاريع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onProjects) { Text("كل المشاريع") }
        }
        if (projects.isEmpty()) {
            EmptyCard("لا توجد مشاريع بعد", "افتح صفحة المشاريع وأضف أول مشروع لتبدأ تسجيل المعاينة والكميات والتنفيذ.")
        } else {
            projects.take(5).forEach { project ->
                Card(Modifier.fillMaxWidth().clickable { onOpenProject(project.id) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(project.name, fontWeight = FontWeight.Bold)
                        Text(project.clientName.ifBlank { "لم يحدد العميل" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(project.location.ifBlank { "الموقع غير محدد" }, fontSize = 12.sp)
                            StatusLabel(project.status)
                        }
                    }
                }
            }
        }
        Text("مسار العمل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مشروع ← معاينة وقياسات ← كميات وتكلفة ← مهام التنفيذ ← ملاحظات المخططات ← تقرير PDF", fontWeight = FontWeight.Medium)
                Text("يتضمن ملف المشروع محرر رسم ثنائي الأبعاد أوليًا. استيراد وتصدير DXF وتحرير DWG والأبعاد الهندسية المتقدمة غير متاحة في هذا الإصدار بعد.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProjectsScreen(
    dao: MaterialsDao,
    projects: List<ProjectEntity>,
    onOpenProject: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المشاريع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Text("＋ مشروع جديد") }
        }
        if (projects.isEmpty()) {
            EmptyCard("ابدأ بمشروعك الأول", "سجل اسم المشروع والعميل والموقع، ثم افتح ملفه لإضافة المعاينة والكميات والمهام.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projects.forEach { project ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenProject(project.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(project.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StatusLabel(project.status)
                            }
                            Text("العميل: ${project.clientName.ifBlank { "غير محدد" }}", fontSize = 13.sp)
                            Text("الموقع: ${project.location.ifBlank { "غير محدد" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("النوع: ${project.projectType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AddProjectDialog(
            onDismiss = { showAdd = false },
            onSave = { name, client, phone, location, type, notes ->
                scope.launch {
                    dao.insertProject(ProjectEntity(UUID.randomUUID().toString(), name.trim(), client.trim(), phone.trim(), location.trim(), type, "جديد", notes.trim()))
                }
                showAdd = false
            }
        )
    }
}

@Composable
private fun ProjectDetailScreen(dao: MaterialsDao, projectId: String, onBack: () -> Unit) {
    val project by dao.observeProject(projectId).collectAsState(initial = null)
    val surveys by dao.observeSurveys(projectId).collectAsState(initial = emptyList())
    val boqItems by dao.observeBoqItems(projectId).collectAsState(initial = emptyList())
    val tasks by dao.observeTasks(projectId).collectAsState(initial = emptyList())
    val drawingNotes by dao.observeDrawingNotes(projectId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSurvey by remember { mutableStateOf(false) }
    var showBoq by remember { mutableStateOf(false) }
    var showTask by remember { mutableStateOf(false) }
    var showDrawingNote by remember { mutableStateOf(false) }
    var reportLines by remember { mutableStateOf(emptyList<String>()) }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val result = runCatching { ProjectPdfExporter.write(context, uri, reportLines) }
            Toast.makeText(context, if (result.isSuccess) "تم حفظ التقرير PDF" else "تعذر إنشاء التقرير", Toast.LENGTH_LONG).show()
        }
    }

    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val currentProject = project!!
    val total = boqItems.sumOf { it.quantity * it.unitRate }
    val costBreakdowns = boqItems.mapNotNull { item ->
        runCatching {
            item to CostCalculator.calculate(
                item.quantity, item.wastePercent, item.purchaseIncrement,
                item.materialUnitRate, item.laborUnitRate, item.unitRate
            )
        }.getOrNull()
    }
    val costedBreakdowns = costBreakdowns.filter { (item, _) -> item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0 }
    val totalCost = costedBreakdowns.sumOf { it.second.totalCostOmr.toDouble() }
    val expectedProfit = costedBreakdowns.sumOf { it.second.expectedProfitOmr.toDouble() }
    val completedTasks = tasks.count { it.status == "مكتمل" }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(currentProject.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(currentProject.clientName.ifBlank { "العميل غير محدد" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusLabel(currentProject.status)
            }
        }
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            listOf("الملخص", "المعاينة", "الكميات", "التنفيذ", "الملاحظات", "الرسم الهندسي").forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
            }
        }
        if (selectedTab == 5) {
            ProjectCadScreen(dao, projectId, Modifier.weight(1f).fillMaxWidth())
        } else {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("مساحات الأرضيات", formatNumber(surveys.sumOf { it.floorAreaM2 }) + " م²", Modifier.weight(1f))
                        MetricCard("قيمة الكميات", formatOmr(total), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("فراغات مقاسة", surveys.size.toString(), Modifier.weight(1f))
                        MetricCard("إنجاز المهام", "$completedTasks / ${tasks.size}", Modifier.weight(1f))
                    }
                    DetailCard("بيانات المشروع") {
                        InfoLine("العميل", currentProject.clientName)
                        InfoLine("الهاتف", currentProject.clientPhone)
                        InfoLine("الموقع", currentProject.location)
                        InfoLine("نوع المشروع", currentProject.projectType)
                        InfoLine("الحالة", currentProject.status)
                        InfoLine("ملاحظات", currentProject.notes)
                    }
                    Text("تغيير حالة المشروع", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("جديد", "معاينة", "تسعير", "قيد التنفيذ", "مكتمل").forEach { status ->
                            FilterChip(
                                selected = currentProject.status == status,
                                onClick = { scope.launch { dao.updateProject(currentProject.copy(status = status)) } },
                                label = { Text(status) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            reportLines = buildList {
                                add("تقرير مشروع مهندس المجلس")
                                add("اسم المشروع: ${currentProject.name}")
                                add("العميل: ${currentProject.clientName}")
                                add("الهاتف: ${currentProject.clientPhone}")
                                add("الموقع: ${currentProject.location}")
                                add("نوع المشروع: ${currentProject.projectType}")
                                add("الحالة: ${currentProject.status}")
                                add("ملاحظات: ${currentProject.notes}")
                                add("")
                                add("المعاينة والقياسات")
                                surveys.forEach { add("${it.spaceName}: طول ${it.lengthM}م، عرض ${it.widthM}م، ارتفاع ${it.heightM}م، أرضية ${formatNumber(it.floorAreaM2)}م²، جدران ${formatNumber(it.wallAreaM2)}م²") }
                                add("")
                                add("جدول الكميات")
                                boqItems.forEach { add("${it.description}: ${it.quantity} ${it.unit} × ${formatOmr(it.unitRate)} = ${formatOmr(it.total)}") }
                                add("إجمالي سعر البيع المتوقع: ${formatOmr(total)}")
                                add("تكلفة المواد والعمالة للبنود المدخلة تكلفتها: ${formatOmr(totalCost)}")
                                add("ربح البنود المدخلة تكلفتها فقط: ${formatOmr(expectedProfit)}")
                                add("بنود لم تُدخل لها تكلفة مواد أو عمالة: ${boqItems.size - costedBreakdowns.size}")
                                costedBreakdowns.forEach { (item, result) ->
                                    add("${item.description}: شراء ${formatNumber(result.purchaseQuantity.toDouble())} ${item.unit}، مواد ${formatOmr(result.materialCostOmr.toDouble())}، عمالة ${formatOmr(result.laborCostOmr.toDouble())}")
                                }
                                add("")
                                add("مهام التنفيذ")
                                tasks.forEach { add("${it.title} — ${it.status}") }
                                add("")
                                add("ملاحظات المخططات والطبقات")
                                drawingNotes.forEach { add("${it.layer} / ${it.title}: ${it.description}") }
                                add("")
                                add("هذا تقرير متابعة وتقدير أولي، ولا يعد اعتمادًا هندسيًا أو مستندًا إنشائيًا مصدقًا.")
                            }
                            pdfLauncher.launch("تقرير-${currentProject.name.replace(" ", "-")}.pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("إنشاء تقرير المشروع PDF") }
                }
                1 -> {
                    SectionHeader("المعاينة الميدانية والقياسات", "احفظ أبعاد كل فراغ، وتحسب المساحات تلقائيًا.")
                    Button(onClick = { showSurvey = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة قياس") }
                    if (surveys.isEmpty()) EmptyCard("لا توجد قياسات", "أضف اسم الفراغ والطول والعرض والارتفاع.")
                    surveys.forEach { item ->
                        DetailCard(item.spaceName) {
                            InfoLine("الأبعاد", "${formatNumber(item.lengthM)} × ${formatNumber(item.widthM)} × ${formatNumber(item.heightM)} م")
                            InfoLine("مساحة الأرضية", "${formatNumber(item.floorAreaM2)} م²")
                            InfoLine("مساحة الجدران الصافية", "${formatNumber(item.wallAreaM2)} م²")
                            InfoLine("ملاحظات", item.notes)
                            TextButton(onClick = { scope.launch { dao.deleteSurvey(item.id) } }) { Text("حذف القياس", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                2 -> {
                    SectionHeader("جدول الكميات والتكلفة", "السعر والتكلفة والربح التقديري بالريال العماني.")
                    MetricCard("سعر البيع المتوقع", formatOmr(total), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("تكلفة المواد والعمالة", formatOmr(totalCost), Modifier.weight(1f))
                        MetricCard(if (costedBreakdowns.isEmpty()) "الربح (أدخل التكلفة)" else "ربح البنود ذات التكلفة", formatOmr(expectedProfit), Modifier.weight(1f))
                    }
                    Button(onClick = { showBoq = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة بند كمية") }
                    if (boqItems.isEmpty()) EmptyCard("جدول الكميات فارغ", "أضف بندًا مع الوحدة والكمية وسعر الوحدة.")
                    boqItems.forEach { item ->
                        DetailCard(item.description) {
                            InfoLine("الفئة / الوحدة", "${item.category} / ${item.unit}")
                            val breakdown = costBreakdowns.firstOrNull { it.first.id == item.id }?.second
                            InfoLine("الكمية الصافية × سعر البيع", "${formatNumber(item.quantity)} × ${formatOmr(item.unitRate)}")
                            InfoLine("الإيراد المتوقع", formatOmr(item.total))
                            if (breakdown != null) {
                                InfoLine("الهدر / وحدة الشراء", "${formatNumber(item.wastePercent)}% / ${formatNumber(item.purchaseIncrement)} ${item.unit}")
                                InfoLine("كمية الشراء بعد التقريب", "${formatNumber(breakdown.purchaseQuantity.toDouble())} ${item.unit}")
                                if (item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0) {
                                    InfoLine("تكلفة المواد", formatOmr(breakdown.materialCostOmr.toDouble()))
                                    InfoLine("تكلفة العمالة", formatOmr(breakdown.laborCostOmr.toDouble()))
                                    InfoLine("التكلفة / الربح", "${formatOmr(breakdown.totalCostOmr.toDouble())} / ${formatOmr(breakdown.expectedProfitOmr.toDouble())}")
                                } else {
                                    InfoLine("التكلفة والربح", "غير محسوبين — أدخل تكلفة المادة أو العمالة")
                                }
                            }
                            TextButton(onClick = { scope.launch { dao.deleteBoqItem(item.id) } }) { Text("حذف البند", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Text("ملاحظة: الأسعار المدخلة تقديرية ومسؤولية المستخدم، ولا يتم جلب أسعار السوق تلقائيًا.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                3 -> {
                    SectionHeader("متابعة التنفيذ", "أنشئ مهام العمل وحدّث حالتها أثناء التنفيذ.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("مكتمل", completedTasks.toString(), Modifier.weight(1f))
                        MetricCard("متبقٍ", (tasks.size - completedTasks).toString(), Modifier.weight(1f))
                    }
                    Button(onClick = { showTask = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة مهمة") }
                    if (tasks.isEmpty()) EmptyCard("لا توجد مهام", "أضف أعمال الموقع ثم حدّث كل مهمة إلى مكتمل.")
                    tasks.forEach { task ->
                        DetailCard(task.title) {
                            InfoLine("التفاصيل", task.details)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                StatusLabel(task.status)
                                TextButton(onClick = {
                                    val next = when (task.status) { "لم يبدأ" -> "قيد التنفيذ"; "قيد التنفيذ" -> "مكتمل"; else -> "لم يبدأ" }
                                    scope.launch { dao.updateTaskStatus(task.id, next) }
                                }) { Text("تحديث الحالة") }
                            }
                            TextButton(onClick = { scope.launch { dao.deleteTask(task.id) } }) { Text("حذف المهمة", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                else -> {
                    SectionHeader("سجل المخططات والطبقات", "دوّن تعليمات وملاحظات كل تخصص مرتبطة بالمشروع.")
                    Button(onClick = { showDrawingNote = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة ملاحظة فنية") }
                    if (drawingNotes.isEmpty()) EmptyCard("لا توجد ملاحظات فنية", "أضف ملاحظات المعماري أو الكهرباء أو السباكة أو بقية الطبقات.")
                    drawingNotes.forEach { note ->
                        DetailCard(note.title) {
                            StatusLabel(note.layer)
                            Text(note.description.ifBlank { "لا توجد تفاصيل" }, fontSize = 13.sp)
                            TextButton(onClick = { scope.launch { dao.deleteDrawingNote(note.id) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "تنبيه فني: هذه الشاشة تحفظ سجل الطبقات والملاحظات فقط. لا تدّعي النسخة الحالية أنها تستورد أو تعدّل ملفات DWG/DXF أو تنتج مخططات CAD تنفيذية.",
                            Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }

    if (showSurvey) AddSurveyDialog(
        onDismiss = { showSurvey = false },
        onSave = { name, length, width, height, openings, notes ->
            scope.launch { dao.insertSurvey(SiteSurveyEntity(UUID.randomUUID().toString(), projectId, name, length, width, height, openings, notes)) }
            showSurvey = false
        }
    )
    if (showBoq) AddBoqDialog(
        onDismiss = { showBoq = false },
        onSave = { description, unit, quantity, rate, category, wastePercent, purchaseIncrement, materialUnitRate, laborUnitRate ->
            scope.launch {
                dao.insertBoqItem(
                    BoqItemEntity(
                        UUID.randomUUID().toString(), projectId, description, unit, quantity, rate, category,
                        wastePercent = wastePercent,
                        purchaseIncrement = purchaseIncrement,
                        materialUnitRate = materialUnitRate,
                        laborUnitRate = laborUnitRate
                    )
                )
            }
            showBoq = false
        }
    )
    if (showTask) AddTaskDialog(
        onDismiss = { showTask = false },
        onSave = { title, details ->
            scope.launch { dao.insertTask(ProjectTaskEntity(UUID.randomUUID().toString(), projectId, title, details)) }
            showTask = false
        }
    )
    if (showDrawingNote) AddDrawingNoteDialog(
        onDismiss = { showDrawingNote = false },
        onSave = { layer, title, description ->
            scope.launch { dao.insertDrawingNote(DrawingNoteEntity(UUID.randomUUID().toString(), projectId, layer, title, description)) }
            showDrawingNote = false
        }
    )
}

@Composable
private fun MaterialsScreen(dao: MaterialsDao) {
    val categories by dao.observeCategories().collectAsState(initial = emptyList())
    val materials by dao.observeMaterials().collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val normalized = remember(search) { MaterialSearchNormalizer.normalize(search) }
    val filtered = materials.filter {
        (selectedCategory == null || it.categoryId == selectedCategory) &&
            (normalized.isBlank() || it.normalizedSearchText.contains(normalized))
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("مكتبة المواد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { showAdd = true }) { Text("＋ مادة") }
        }
        Text("كتالوج تعريفي محلي، دون أسعار أو مخزون أو موردين.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("ابحث بالعربية أو الإنجليزية") }, singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selectedCategory == null, { selectedCategory = null }, label = { Text("الكل") })
            categories.forEach { category ->
                FilterChip(selectedCategory == category.id, { selectedCategory = if (selectedCategory == category.id) null else category.id }, label = { Text(category.nameAr) })
            }
        }
        Text("${filtered.size} مادة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) EmptyCard("لا توجد مواد مطابقة", "أضف المادة بنفسك إلى التصنيف المناسب.")
            filtered.forEach { material ->
                DetailCard(material.nameAr) {
                    if (material.nameEn.isNotBlank()) Text(material.nameEn, fontSize = 12.sp)
                    Text(categories.firstOrNull { it.id == material.categoryId }?.nameAr.orEmpty(), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    if (material.shortNote.isNotBlank()) Text(material.shortNote, fontSize = 13.sp)
                }
            }
        }
    }
    if (showAdd) AddMaterialDialog(categories, { showAdd = false }) { categoryId, nameAr, nameEn, note, aliases ->
        val searchText = MaterialSearchNormalizer.normalize("$nameAr $nameEn $aliases")
        scope.launch {
            dao.insertMaterials(listOf(MaterialEntity(UUID.randomUUID().toString(), categoryId, nameAr.trim(), nameEn.trim(), shortNote = note.trim(), aliases = aliases.trim(), normalizedSearchText = searchText)))
        }
        showAdd = false
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun EmptyCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isNotBlank()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(value, modifier = Modifier.weight(1f), fontSize = 13.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLabel(status: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(status, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("مجلس") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشروع جديد") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المشروع *") }, singleLine = true)
                OutlinedTextField(client, { client = it }, label = { Text("اسم العميل") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("الموقع") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("نوع المشروع") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات أولية") })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, client, phone, location, type, notes) }) { Text("حفظ المشروع") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddSurveyDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("3.0") }
    var openings by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && (length.toDoubleOrNull() ?: 0.0) > 0 && (width.toDoubleOrNull() ?: 0.0) > 0 && (height.toDoubleOrNull() ?: 0.0) > 0 && (openings.toDoubleOrNull() ?: -1.0) >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة قياس ميداني") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الفراغ *") }, singleLine = true)
                OutlinedTextField(length, { length = it }, label = { Text("الطول بالمتر *") }, singleLine = true)
                OutlinedTextField(width, { width = it }, label = { Text("العرض بالمتر *") }, singleLine = true)
                OutlinedTextField(height, { height = it }, label = { Text("الارتفاع بالمتر *") }, singleLine = true)
                OutlinedTextField(openings, { openings = it }, label = { Text("مساحة الفتحات م²") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                Text("تحسب مساحة الأرضية والجدران الصافية تلقائيًا بعد الحفظ.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name, length.toDouble()!!, width.toDouble()!!, height.toDouble()!!, openings.toDouble()!!, notes) }) { Text("حفظ القياس") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddBoqDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double, String, Double, Double, Double, Double) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("م²") }
    var quantity by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var wastePercent by remember { mutableStateOf("0") }
    var purchaseIncrement by remember { mutableStateOf("1") }
    var materialUnitRate by remember { mutableStateOf("0") }
    var laborUnitRate by remember { mutableStateOf("0") }
    var category by remember { mutableStateOf("تشطيبات") }
    val parsedWaste = wastePercent.toDoubleOrNull()
    val parsedIncrement = purchaseIncrement.toDoubleOrNull()
    val parsedMaterialRate = materialUnitRate.toDoubleOrNull()
    val parsedLaborRate = laborUnitRate.toDoubleOrNull()
    val parsedQuantity = quantity.toDoubleOrNull()
    val parsedRate = rate.toDoubleOrNull()
    val valid = description.isNotBlank() && unit.isNotBlank() &&
        (parsedQuantity ?: 0.0) > 0 && (parsedRate ?: -1.0) >= 0 &&
        (parsedWaste ?: -1.0) >= 0 && (parsedIncrement ?: 0.0) > 0 &&
        (parsedMaterialRate ?: -1.0) >= 0 && (parsedLaborRate ?: -1.0) >= 0 &&
        listOf(parsedQuantity, parsedRate, parsedWaste, parsedIncrement, parsedMaterialRate, parsedLaborRate).all { it?.isFinite() == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة بند إلى جدول الكميات") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("وصف البند *") })
                OutlinedTextField(category, { category = it }, label = { Text("الفئة") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة *") }, singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("الكمية الصافية *") }, singleLine = true)
                OutlinedTextField(wastePercent, { wastePercent = it }, label = { Text("نسبة الهدر %") }, singleLine = true)
                OutlinedTextField(purchaseIncrement, { purchaseIncrement = it }, label = { Text("وحدة الشراء / خطوة التقريب") }, singleLine = true)
                OutlinedTextField(materialUnitRate, { materialUnitRate = it }, label = { Text("تكلفة المادة للوحدة ر.ع") }, singleLine = true)
                OutlinedTextField(laborUnitRate, { laborUnitRate = it }, label = { Text("تكلفة العمالة للوحدة ر.ع") }, singleLine = true)
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر البيع للوحدة ر.ع *") }, singleLine = true)
                Text("الشراء = الكمية الصافية × (1 + الهدر)، ثم التقريب لوحدة الشراء. أدخل الأسعار الفعلية؛ النتيجة تقديرية وتحتاج مراجعة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(description, unit, parsedQuantity!!, parsedRate!!, category, parsedWaste!!, parsedIncrement!!, parsedMaterialRate!!, parsedLaborRate!!)
        }) { Text("حفظ البند") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مهمة تنفيذ جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("اسم المهمة *") })
                OutlinedTextField(details, { details = it }, label = { Text("التفاصيل") })
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, details) }) { Text("حفظ المهمة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddDrawingNoteDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val layers = listOf("معماري", "كهرباء وإنارة", "سباكة", "صرف صحي", "تكييف وتهوية", "تشطيبات", "أثاث")
    var layer by remember { mutableStateOf(layers.first()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملاحظة فنية / طبقة مخطط") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر التخصص")
                layers.forEach { value ->
                    FilterChip(selected = layer == value, onClick = { layer = value }, label = { Text(value) })
                }
                OutlinedTextField(title, { title = it }, label = { Text("عنوان الملاحظة *") })
                OutlinedTextField(description, { description = it }, label = { Text("التفاصيل والتعليمات") })
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(layer, title, description) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
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
        title = { Text("إضافة مادة إلى المكتبة") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(categories.firstOrNull { it.id == categoryId }?.nameAr ?: "اختر التصنيف")
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.nameAr) }, onClick = { categoryId = category.id; expanded = false })
                        }
                    }
                }
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم المادة بالعربية *") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم بالإنجليزية") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة تعريفية") })
                OutlinedTextField(aliases, { aliases = it }, label = { Text("أسماء بديلة للبحث") })
            }
        },
        confirmButton = { TextButton(enabled = nameAr.isNotBlank() && categoryId.isNotBlank(), onClick = { onSave(categoryId, nameAr, nameEn, note, aliases) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun formatOmr(value: Double): String = String.format(Locale.US, "%.3f ر.ع", value)
private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
