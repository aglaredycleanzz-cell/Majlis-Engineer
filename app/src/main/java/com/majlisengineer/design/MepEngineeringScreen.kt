package com.majlisengineer.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private enum class MepView(val title: String) { ALL("التنسيق"), WATER("السباكة"), POWER("الكهرباء") }

@Composable
fun MepEngineeringScreen(modifier: Modifier = Modifier) {
    var lengthText by remember { mutableStateOf("8.0") }
    var widthText by remember { mutableStateOf("5.0") }
    var heightText by remember { mutableStateOf("3.2") }
    var view by remember { mutableStateOf(MepView.ALL) }
    var camera by remember { mutableFloatStateOf(0.0f) }
    var showStructure by remember { mutableStateOf(true) }
    var showDimensions by remember { mutableStateOf(true) }
    val length = lengthText.toDoubleOrNull()?.coerceIn(2.0, 30.0) ?: 8.0
    val width = widthText.toDoubleOrNull()?.coerceIn(2.0, 30.0) ?: 5.0
    val height = heightText.toDoubleOrNull()?.coerceIn(2.2, 8.0) ?: 3.2

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("مختبر التمديدات الهندسية", style = MaterialTheme.typography.headlineSmall)
        Text("نموذج مجسم للفراغ مع طبقات السباكة والكهرباء ومساراتها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MepView.values().forEach { item ->
                FilterChip(selected = view == item, onClick = { view = item }, label = { Text(item.title) })
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("منظور ثلاثي الأبعاد", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { camera -= 0.18f }) { Text("↶") }
                        TextButton(onClick = { camera = 0f }) { Text("إعادة") }
                        TextButton(onClick = { camera += 0.18f }) { Text("↷") }
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF101B25), RoundedCornerShape(12.dp))) {
                    val c = cos(camera); val s = sin(camera)
                    val scale = minOf(size.width / (length.toFloat() + width.toFloat() * 0.9f + 2f), size.height / (height.toFloat() * 1.5f + 8f)) * 0.72f
                    val ox = size.width * 0.49f
                    val oy = size.height * 0.76f
                    fun p(x: Float, y: Float, z: Float): Offset {
                        val rx = x * c - y * s
                        val ry = x * s + y * c
                        return Offset(ox + (rx - ry) * scale * 0.78f, oy - (rx + ry) * scale * 0.42f - z * scale * 1.05f)
                    }
                    fun polygon(points: List<Offset>, color: Color) {
                        val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                        drawPath(path, color)
                    }
                    val l = length.toFloat(); val w = width.toFloat(); val h = height.toFloat()
                    val a = p(0f,0f,0f); val b = p(l,0f,0f); val d = p(0f,w,0f); val e = p(l,w,0f)
                    val at = p(0f,0f,h); val bt = p(l,0f,h); val dt = p(0f,w,h); val et = p(l,w,h)
                    polygon(listOf(a,b,e,d), Color(0xFF344454))
                    polygon(listOf(a,d,dt,at), Color(0xFF526575))
                    polygon(listOf(a,b,bt,at), Color(0xFF405464))
                    polygon(listOf(at,bt,et,dt), Color(0x223A5268))
                    if (showStructure) {
                        val edges = listOf(a to b, b to e, e to d, d to a, a to at, b to bt, d to dt, at to bt, at to dt)
                        edges.forEach { (u,v) -> drawLine(Color(0xFF9EB0BF), u, v, 2.2f) }
                    }
                    // Main water supply: service entry, distribution and fixture drops.
                    if (view != MepView.POWER) {
                        val water = Color(0xFF36C5E8)
                        val supply = listOf(p(0f,0f,0.25f), p(0.5f,0f,0.25f), p(l*0.55f,0f,0.25f), p(l*0.55f,w*0.42f,0.25f), p(l*0.55f,w*0.42f,h*0.62f))
                        for (i in 0 until supply.lastIndex) drawLine(water, supply[i], supply[i+1], 5f, cap = StrokeCap.Round)
                        val drain = listOf(p(l*0.18f,w*0.82f,0.12f), p(l*0.5f,w*0.82f,0.12f), p(l*0.82f,w*0.82f,0.12f), p(l*0.82f,w,0.12f))
                        for (i in 0 until drain.lastIndex) drawLine(Color(0xFF8CA3B3), drain[i], drain[i+1], 4f, cap = StrokeCap.Round)
                        listOf(p(l*0.22f,w*0.42f,0.25f),p(l*0.55f,w*0.42f,0.25f),p(l*0.82f,w*0.42f,0.25f)).forEach {
                            drawCircle(water, 5f, it); drawCircle(Color.White, 2f, it)
                        }
                    }
                    // Electrical conduit trunk, branches and outlet drops.
                    if (view != MepView.WATER) {
                        val wire = Color(0xFFFFC857)
                        val trunk = listOf(p(0f,w,h*0.92f),p(l*0.5f,w,h*0.92f),p(l*0.5f,w*0.5f,h*0.92f),p(l,w*0.5f,h*0.92f))
                        for (i in 0 until trunk.lastIndex) drawLine(wire, trunk[i], trunk[i+1], 4f, cap = StrokeCap.Round)
                        val branches = listOf(
                            listOf(p(l*0.22f,w,h*0.92f),p(l*0.22f,w*0.55f,h*0.92f),p(l*0.22f,w*0.55f,0.55f)),
                            listOf(p(l*0.72f,w*0.5f,h*0.92f),p(l*0.72f,w*0.5f,0.55f)),
                            listOf(p(l*0.5f,w*0.5f,h*0.92f),p(l*0.5f,w*0.5f,h))
                        )
                        branches.forEach { line -> for (i in 0 until line.lastIndex) drawLine(wire,line[i],line[i+1],3.5f,cap=StrokeCap.Round) }
                        listOf(p(l*0.22f,w*0.55f,0.55f),p(l*0.72f,w*0.5f,0.55f),p(l*0.5f,w*0.5f,h)).forEach {
                            drawCircle(wire, 6f, it); drawCircle(Color(0xFF263746), 2.5f, it)
                        }
                    }
                    if (showDimensions) {
                        drawLine(Color(0xFFB5C6D3), p(0f,w+0.35f,0f), p(l,w+0.35f,0f), 1.5f)
                        drawLine(Color(0xFFB5C6D3), p(-0.35f,0f,0f), p(-0.35f,w,0f), 1.5f)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    LegendDot(Color(0xFF36C5E8), "تغذية المياه")
                    LegendDot(Color(0xFF8CA3B3), "الصرف")
                    LegendDot(Color(0xFFFFC857), "الكهرباء")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("اسحب منظور الكاميرا بالأزرار", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("المقياس: متر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("أبعاد الفراغ", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DimensionField("الطول (م)", lengthText, { lengthText = it }, Modifier.weight(1f))
            DimensionField("العرض (م)", widthText, { widthText = it }, Modifier.weight(1f))
            DimensionField("الارتفاع (م)", heightText, { heightText = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showStructure, onClick = { showStructure = !showStructure }, label = { Text("هيكل الغرفة") })
            FilterChip(selected = showDimensions, onClick = { showDimensions = !showDimensions }, label = { Text("خطوط الأبعاد") })
        }

        Text("لوحة الأنظمة", style = MaterialTheme.typography.titleMedium)
        if (view != MepView.POWER) SystemCard("السباكة", Color(0xFF36C5E8),
            listOf("خط تغذية رئيسي ونقاط توزيع", "خط صرف رئيسي وفرعي", "نقاط أجهزة صحية ومحابس", "خصائص الأقطار والمناسيب قيد البناء"))
        if (view != MepView.WATER) SystemCard("الكهرباء", Color(0xFFFFC857),
            listOf("مسار مواسير كهربائية رئيسي", "تفريعات الإنارة والأفياش", "نقاط المفاتيح ولوحة التوزيع", "أحمال الدوائر والقواطع قيد البناء"))
        if (view == MepView.ALL) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("تنسيق الأنظمة", style = MaterialTheme.typography.titleSmall)
                    Text("يعرض هذا الوضع مسارات النظامين معًا لتسهيل مراجعة التقاطعات بصريًا. فحص التعارضات الهندسي الآلي سيضاف في مرحلة لاحقة.", fontSize = 12.sp)
                }
            }
        }
        Text("تنبيه هندسي: المسارات الظاهرة نموذج توضيحي أولي وليست مخطط تنفيذ أو اعتماد. يجب إدخال مواقع الأجهزة والمناسيب والأقطار والأحمال الفعلية ومراجعتها من مهندس مختص قبل التنفيذ.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(9.dp)) { drawCircle(color) }
        Text(label, fontSize = 10.sp)
    }
}

@Composable
private fun DimensionField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value, onChange, modifier = modifier, label = { Text(label, fontSize = 11.sp) }, singleLine = true)
}

@Composable
private fun SystemCard(title: String, color: Color, lines: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            lines.forEach { Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
