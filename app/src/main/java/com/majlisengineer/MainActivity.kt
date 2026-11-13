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
import com.majlisengineer.costs.ProjectCostAdjustments
import com.majlisengineer.costs.ProjectCostLine
import com.majlisengineer.costs.ProjectCostSummaryCalculator
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.time.LocalDate

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
                    MainDestination.Materials -> MaterialManagementScreen(dao)
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
                Text("يتضمن ملف المشروع محرر 2D واستيراد/تصدير DXF لعناصر محدودة مع تحذيرات وحفظ الأصل؛ DWG والأبعاد الهندسية المتقدمة غير متاحة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var editingProject by remember { mutableStateOf<ProjectEntity?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("المشاريع", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = { editingProject = null; showAdd = true }) { Text("＋ مشروع جديد") }
        }
        if (projects.isEmpty()) {
            EmptyCard("ابدأ بمشروعك الأول", "سجل اسم المشروع والعميل والموقع، ثم افتح ملفه لإضافة المعاينة والكميات والمهام.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projects.forEach { project ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(project.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                StatusLabel(project.status)
                            }
                            Text("العميل: ${project.clientName.ifBlank { "غير محدد" }}", fontSize = 13.sp)
                            Text("الموقع: ${project.location.ifBlank { "غير محدد" }}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("النوع: ${project.projectType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            if (project.startDate.isNotBlank()) Text("البدء: ${project.startDate}", fontSize = 12.sp)
                            if (project.expectedDeliveryDate.isNotBlank()) Text("التسليم المتوقع: ${project.expectedDeliveryDate}", fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onOpenProject(project.id) }) { Text("فتح الملف") }
                                TextButton(onClick = { editingProject = project; showAdd = true }) { Text("تعديل البيانات") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        AddProjectDialog(
            existing = editingProject,
            onDismiss = { showAdd = false },
            onSave = { project ->
                scope.launch { dao.insertProject(project) }
                showAdd = false
                editingProject = null
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
    val costSettings by dao.observeProjectCostSettings(projectId).collectAsState(initial = null)
    val savedInteriorDesign by dao.observeInteriorDesign(projectId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSurvey by remember { mutableStateOf(false) }
    var showBoq by remember { mutableStateOf(false) }
    var editingSurvey by remember { mutableStateOf<SiteSurveyEntity?>(null) }
    var editingBoqItem by remember { mutableStateOf<BoqItemEntity?>(null) }
    var showTask by remember { mutableStateOf(false) }
    var showDrawingNote by remember { mutableStateOf(false) }
    var showCostSettings by remember { mutableStateOf(false) }
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
    val enteredSalesTotal = boqItems.sumOf { it.quantity * it.unitRate }
    val costBreakdowns = boqItems.mapNotNull { item ->
        runCatching {
            item to CostCalculator.calculate(
                item.quantity, item.wastePercent, item.purchaseIncrement,
                item.materialUnitRate, item.laborUnitRate, item.unitRate
            )
        }.getOrNull()
    }
    val costedBreakdowns = costBreakdowns.filter { (item, _) -> item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0 }
    val effectiveSettings = costSettings ?: ProjectCostSettingsEntity(projectId, updatedAt = 0L)
    val summaryResult = runCatching { ProjectCostSummaryCalculator.summarize(
        costBreakdowns.map { (item, breakdown) ->
            ProjectCostLine(breakdown, item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0)
        },
        ProjectCostAdjustments(
            transportOmr = effectiveSettings.transportOmr,
            additionalExpensesOmr = effectiveSettings.additionalExpensesOmr,
            discountPercent = effectiveSettings.discountPercent,
            discountFixedOmr = effectiveSettings.discountFixedOmr
        )
    ) }
    val projectCostSummary = summaryResult.getOrElse {
        ProjectCostSummaryCalculator.summarize(
            costBreakdowns.map { (item, breakdown) -> ProjectCostLine(breakdown, item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0) }
        )
    }
    val total = if (costBreakdowns.size == boqItems.size) projectCostSummary.salesSubtotalOmr.toDouble() else enteredSalesTotal
    val totalCost = projectCostSummary.estimatedCostOmr.toDouble()
    val expectedProfit = if (summaryResult.isSuccess && costBreakdowns.size == boqItems.size) projectCostSummary.expectedProfitOmr?.toDouble() else null
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
            listOf("الملخص", "المعاينة", "الكميات", "التنفيذ", "مواد المشروع", "الملاحظات", "الرسم الهندسي", "التصميم الداخلي").forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
            }
        }
        if (selectedTab == 6) {
            ProjectCadScreen(dao, projectId, Modifier.weight(1f).fillMaxWidth())
        } else if (selectedTab == 7) {
            com.majlisengineer.design.InteriorDesignScreen(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                savedDesign = savedInteriorDesign,
                onSaveDesign = { room ->
                    scope.launch {
                        dao.saveInteriorDesign(InteriorDesignEntity(
                            projectId, room.lengthM, room.widthM, room.heightM, room.doorWidthM, room.windowCount,
                            room.style.name, room.seating.name, room.palette.name
                        ))
                    }
                    Toast.makeText(context, "حُفظ التصميم وربط بالمشروع — مساحة الأرضية ${String.format(Locale.US, "%.2f", room.floorAreaM2)} م²", Toast.LENGTH_SHORT).show()
                }
            )
        } else if (selectedTab == 4) {
            ProjectMaterialsScreen(dao, projectId, Modifier.weight(1f).fillMaxWidth())
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
                        InfoLine("تاريخ البدء", currentProject.startDate)
                        InfoLine("التسليم المتوقع", currentProject.expectedDeliveryDate)
                        InfoLine("ملاحظات", currentProject.notes)
                    }
                    Text("تغيير حالة المشروع", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("جديد", "معاينة", "مخططات", "تسعير", "قيد التنفيذ", "مكتمل", "متوقف").forEach { status ->
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
                                add("تاريخ البدء: ${currentProject.startDate.ifBlank { "غير محدد" }}")
                                add("التسليم المتوقع: ${currentProject.expectedDeliveryDate.ifBlank { "غير محدد" }}")
                                add("ملاحظات: ${currentProject.notes}")
                                add("")
                                add("المعاينة والقياسات")
                                surveys.forEach { add("${it.spaceName}: طول ${it.lengthM}م، عرض ${it.widthM}م، ارتفاع ${it.heightM}م، أرضية ${formatNumber(it.floorAreaM2)}م²، جدران ${formatNumber(it.wallAreaM2)}م²") }
                                add("")
                                add("جدول الكميات")
                                boqItems.forEach { add("${it.description}: ${it.quantity} ${it.unit} × ${formatOmr(it.unitRate)} = ${formatOmr(it.total)}") }
                                add("إجمالي سعر البيع المتوقع: ${formatOmr(total)}")
                                add("تكلفة المواد والعمالة والنقل والمصاريف للبنود القابلة للحساب: ${formatOmr(totalCost)}")
                                add("صافي بيع البنود القابلة للحساب بعد الخصومات: ${formatOmr(projectCostSummary.netSalesOmr.toDouble())}")
                                add("الربح المتوقع: ${expectedProfit?.let(::formatOmr) ?: "غير محدد — توجد تكلفة ناقصة أو بند غير صالح"}")
                                add("الهامش المتوقع: ${if (expectedProfit == null) "غير محدد" else projectCostSummary.expectedMarginPercent?.let { formatNumber(it.toDouble()) + "%" } ?: "غير محدد"}")
                                add("بنود بلا تكلفة مدخلة: ${projectCostSummary.uncostedLineCount}")
                                add("بنود تعذّر حسابها: ${boqItems.size - costBreakdowns.size}")
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
                    Button(onClick = { editingSurvey = null; showSurvey = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة قياس") }
                    if (surveys.isEmpty()) EmptyCard("لا توجد قياسات", "أضف اسم الفراغ والطول والعرض والارتفاع.")
                    surveys.forEach { item ->
                        DetailCard(item.spaceName) {
                            InfoLine("الأبعاد", "${formatNumber(item.lengthM)} × ${formatNumber(item.widthM)} × ${formatNumber(item.heightM)} م")
                            InfoLine("مساحة الأرضية", "${formatNumber(item.floorAreaM2)} م²")
                            InfoLine("مساحة الجدران الصافية", "${formatNumber(item.wallAreaM2)} م²")
                            InfoLine("ملاحظات", item.notes)
                            TextButton(onClick = { editingSurvey = item; showSurvey = true }) { Text("تعديل القياس") }
                            TextButton(onClick = { scope.launch { dao.deleteSurvey(item.id) } }) { Text("حذف القياس", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                2 -> {
                    SectionHeader("جدول الكميات والتكلفة", "السعر والتكلفة والخصومات والربح التقديري بالريال العماني.")
                    MetricCard("سعر البيع المتوقع", formatOmr(total), Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("التكلفة المقدرة شاملة", formatOmr(totalCost), Modifier.weight(1f))
                        MetricCard("الربح المتوقع", expectedProfit?.let(::formatOmr) ?: "غير محدد", Modifier.weight(1f))
                    }
                    DetailCard("ملخص مالي للمشروع") {
                        if (summaryResult.isFailure) {
                            Text("تعذر تطبيق إعدادات الخصم الحالية على قيمة البيع الجديدة. عُرضت المجاميع دون خصم؛ راجع إعدادات المشروع.", color = MaterialTheme.colorScheme.error)
                        }
                        if (costBreakdowns.size != boqItems.size) {
                            Text("توجد بنود غير صالحة للحساب؛ قيمة التكلفة لا تشملها والربح غير محدد.", color = MaterialTheme.colorScheme.error)
                        }
                        InfoLine("المواد", formatOmr(projectCostSummary.materialCostOmr.toDouble()))
                        InfoLine("العمالة", formatOmr(projectCostSummary.laborCostOmr.toDouble()))
                        InfoLine("النقل", formatOmr(projectCostSummary.transportOmr.toDouble()))
                        InfoLine("المصاريف الإضافية", formatOmr(projectCostSummary.additionalExpensesOmr.toDouble()))
                        InfoLine("الخصومات", formatOmr(projectCostSummary.totalDiscountOmr.toDouble()))
                        InfoLine("صافي بيع البنود المحسوبة", formatOmr(projectCostSummary.netSalesOmr.toDouble()))
                        InfoLine("هامش الربح", if (expectedProfit == null) "غير محدد" else projectCostSummary.expectedMarginPercent?.let { formatNumber(it.toDouble()) + "%" } ?: "غير محدد")
                        InfoLine("بنود بلا تكلفة مدخلة", projectCostSummary.uncostedLineCount.toString())
                        if (costBreakdowns.size != boqItems.size) InfoLine("بنود غير صالحة للحساب", (boqItems.size - costBreakdowns.size).toString())
                        OutlinedButton(onClick = { showCostSettings = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("إعداد النقل والمصاريف والخصومات")
                        }
                    }
                    Button(onClick = { editingBoqItem = null; showBoq = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ إضافة بند كمية") }
                    if (boqItems.isEmpty()) EmptyCard("جدول الكميات فارغ", "أضف بندًا مع الوحدة والكمية وسعر الوحدة.")
                    boqItems.forEach { item ->
                        DetailCard(item.description) {
                            InfoLine("الفئة / الوحدة", "${item.category} / ${item.unit}")
                            val breakdown = costBreakdowns.firstOrNull { it.first.id == item.id }?.second
                            InfoLine("الكمية الصافية × سعر البيع", "${formatNumber(item.quantity)} × ${formatOmr(item.unitRate)}")
                            InfoLine("الإيراد المتوقع", formatOmr(item.total))
                            if (breakdown != null) {
                                InfoLine("الهدر / وحدة الشراء", "${formatNumber(item.wastePercent)}% / ${formatNumber(item.purchaseIncrement)} ${item.unit}")
                                InfoLine("كمية الهدر", "${formatNumber(breakdown.wasteQuantity.toDouble())} ${item.unit}")
                                InfoLine("كمية الشراء بعد التقريب", "${formatNumber(breakdown.purchaseQuantity.toDouble())} ${item.unit}")
                                InfoLine("فائض تقريب الشراء", "${formatNumber(breakdown.roundingSurplus.toDouble())} ${item.unit}")
                                if (item.materialUnitRate > 0.0 || item.laborUnitRate > 0.0) {
                                    InfoLine("تكلفة المواد", formatOmr(breakdown.materialCostOmr.toDouble()))
                                    InfoLine("تكلفة العمالة", formatOmr(breakdown.laborCostOmr.toDouble()))
                                    InfoLine("التكلفة / الربح", "${formatOmr(breakdown.totalCostOmr.toDouble())} / ${formatOmr(breakdown.expectedProfitOmr.toDouble())}")
                                } else {
                                    InfoLine("التكلفة والربح", "غير محسوبين — أدخل تكلفة المادة أو العمالة")
                                }
                            }
                            TextButton(onClick = { editingBoqItem = item; showBoq = true }) { Text("تعديل البند") }
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
                            "تنبيه فني: هذا السجل يحفظ الملاحظات فقط. استيراد DXF محدود متاح في تبويب الرسم الهندسي؛ لا يدعم التطبيق DWG أو التحرير الكامل ولا ينتج مخططًا تنفيذيًا معتمدًا.",
                            Modifier.padding(14.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        }
    }

    if (showCostSettings) ProjectCostSettingsDialog(
        current = effectiveSettings,
        salesSubtotalOmr = projectCostSummary.salesSubtotalOmr.toDouble(),
        onDismiss = { showCostSettings = false },
        onSave = { transport, expenses, percent, fixed ->
            scope.launch {
                dao.saveProjectCostSettings(ProjectCostSettingsEntity(
                    projectId, transport, expenses, percent, fixed, System.currentTimeMillis()
                ))
            }
            showCostSettings = false
        }
    )
    if (showSurvey) AddSurveyDialog(
        projectId = projectId,
        existing = editingSurvey,
        onDismiss = { showSurvey = false; editingSurvey = null },
        onSave = { survey ->
            scope.launch { if (editingSurvey == null) dao.insertSurvey(survey) else dao.updateSurvey(survey) }
            showSurvey = false
            editingSurvey = null
        }
    )
    if (showBoq) AddBoqDialog(
        projectId = projectId,
        existing = editingBoqItem,
        onDismiss = { showBoq = false; editingBoqItem = null },
        onSave = { item ->
            scope.launch {
                if (editingBoqItem == null) dao.insertBoqItem(item) else dao.updateBoqItem(item)
            }
            showBoq = false
            editingBoqItem = null
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
    existing: ProjectEntity?,
    onDismiss: () -> Unit,
    onSave: (ProjectEntity) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var client by remember(existing?.id) { mutableStateOf(existing?.clientName.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.clientPhone.orEmpty()) }
    var location by remember(existing?.id) { mutableStateOf(existing?.location.orEmpty()) }
    var type by remember(existing?.id) { mutableStateOf(existing?.projectType ?: "مجلس") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: "جديد") }
    var startDate by remember(existing?.id) { mutableStateOf(existing?.startDate.orEmpty()) }
    var deliveryDate by remember(existing?.id) { mutableStateOf(existing?.expectedDeliveryDate.orEmpty()) }
    val statuses = listOf("جديد", "معاينة", "مخططات", "تسعير", "قيد التنفيذ", "مكتمل", "متوقف")
    val datesValid = validOptionalIsoDate(startDate) && validOptionalIsoDate(deliveryDate)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "مشروع جديد" else "تعديل بيانات المشروع") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم المشروع *") }, singleLine = true)
                OutlinedTextField(client, { client = it }, label = { Text("اسم العميل") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("الموقع") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("نوع المشروع") }, singleLine = true)
                Text("حالة المشروع", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statuses.forEach { value ->
                        FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value) })
                    }
                }
                OutlinedTextField(startDate, { startDate = it }, label = { Text("تاريخ البدء YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(deliveryDate, { deliveryDate = it }, label = { Text("موعد التسليم YYYY-MM-DD") }, singleLine = true)
                if (!datesValid) Text("أدخل التاريخ بصيغة السنة-الشهر-اليوم، أو اتركه فارغًا.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات أولية") })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && datesValid, onClick = {
            onSave(
                ProjectEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    clientName = client.trim(),
                    clientPhone = phone.trim(),
                    location = location.trim(),
                    projectType = type.trim().ifBlank { "مجلس" },
                    status = status,
                    notes = notes.trim(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    startDate = startDate.trim(),
                    expectedDeliveryDate = deliveryDate.trim()
                )
            )
        }) { Text(if (existing == null) "حفظ المشروع" else "حفظ التعديلات") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun validOptionalIsoDate(value: String): Boolean =
    value.isBlank() || runCatching { LocalDate.parse(value); true }.getOrDefault(false)

@Composable
private fun AddSurveyDialog(
    projectId: String,
    existing: SiteSurveyEntity?,
    onDismiss: () -> Unit,
    onSave: (SiteSurveyEntity) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.spaceName.orEmpty()) }
    var length by remember(existing?.id) { mutableStateOf(existing?.lengthM?.toString().orEmpty()) }
    var width by remember(existing?.id) { mutableStateOf(existing?.widthM?.toString().orEmpty()) }
    var height by remember(existing?.id) { mutableStateOf(existing?.heightM?.toString() ?: "3.0") }
    var openings by remember(existing?.id) { mutableStateOf(existing?.openingsAreaM2?.toString() ?: "0") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    val parsedLength = length.toDoubleOrNull()
    val parsedWidth = width.toDoubleOrNull()
    val parsedHeight = height.toDoubleOrNull()
    val parsedOpenings = openings.toDoubleOrNull()
    val grossWallArea = if (parsedLength != null && parsedWidth != null && parsedHeight != null) 2.0 * (parsedLength + parsedWidth) * parsedHeight else 0.0
    val floorArea = if (parsedLength != null && parsedWidth != null) parsedLength * parsedWidth else 0.0
    val valid = name.isNotBlank() && (parsedLength ?: 0.0) > 0 && (parsedWidth ?: 0.0) > 0 &&
        (parsedHeight ?: 0.0) > 0 && (parsedOpenings ?: -1.0) >= 0 && (parsedOpenings ?: 0.0) <= grossWallArea &&
        listOf(parsedLength, parsedWidth, parsedHeight, parsedOpenings).all { it?.isFinite() == true } &&
        grossWallArea.isFinite() && floorArea.isFinite()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "إضافة قياس ميداني" else "تعديل القياس الميداني") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الفراغ *") }, singleLine = true)
                OutlinedTextField(length, { length = it }, label = { Text("الطول بالمتر *") }, singleLine = true)
                OutlinedTextField(width, { width = it }, label = { Text("العرض بالمتر *") }, singleLine = true)
                OutlinedTextField(height, { height = it }, label = { Text("الارتفاع بالمتر *") }, singleLine = true)
                OutlinedTextField(openings, { openings = it }, label = { Text("مساحة الفتحات م²") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                Text("تحسب مساحة الأرضية والجدران الصافية تلقائيًا. يجب ألا تتجاوز الفتحات مساحة الجدران الإجمالية.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            onSave(
                SiteSurveyEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    spaceName = name.trim(),
                    lengthM = parsedLength!!,
                    widthM = parsedWidth!!,
                    heightM = parsedHeight!!,
                    openingsAreaM2 = parsedOpenings!!,
                    notes = notes.trim(),
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
        }) { Text(if (existing == null) "حفظ القياس" else "حفظ التعديل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddBoqDialog(
    projectId: String,
    existing: BoqItemEntity?,
    onDismiss: () -> Unit,
    onSave: (BoqItemEntity) -> Unit
) {
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit ?: "م²") }
    var quantity by remember(existing?.id) { mutableStateOf(existing?.quantity?.toString().orEmpty()) }
    var rate by remember(existing?.id) { mutableStateOf(existing?.unitRate?.toString().orEmpty()) }
    var wastePercent by remember(existing?.id) { mutableStateOf(existing?.wastePercent?.toString() ?: "0") }
    var purchaseIncrement by remember(existing?.id) { mutableStateOf(existing?.purchaseIncrement?.toString() ?: "1") }
    var materialUnitRate by remember(existing?.id) { mutableStateOf(existing?.materialUnitRate?.toString() ?: "0") }
    var laborUnitRate by remember(existing?.id) { mutableStateOf(existing?.laborUnitRate?.toString() ?: "0") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "تشطيبات") }
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
        title = { Text(if (existing == null) "إضافة بند إلى جدول الكميات" else "تعديل بند الحصر") },
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
            onSave(
                BoqItemEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    projectId = projectId,
                    description = description.trim(),
                    unit = unit.trim(),
                    quantity = parsedQuantity!!,
                    unitRate = parsedRate!!,
                    category = category.trim().ifBlank { "عام" },
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    wastePercent = parsedWaste!!,
                    purchaseIncrement = parsedIncrement!!,
                    materialUnitRate = parsedMaterialRate!!,
                    laborUnitRate = parsedLaborRate!!
                )
            )
        }) { Text(if (existing == null) "حفظ البند" else "حفظ التعديل") } },
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

private fun formatOmr(value: Double): String = String.format(Locale.US, "%.3f ر.ع", value)
private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
