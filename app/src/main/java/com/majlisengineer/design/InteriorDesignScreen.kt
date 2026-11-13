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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun InteriorDesignScreen(
    modifier: Modifier = Modifier,
    savedDesign: com.majlisengineer.data.InteriorDesignEntity? = null,
    onSaveDesign: (InteriorRoom) -> Unit = {}
) {
    var length by remember { mutableStateOf("8.0") }
    var width by remember { mutableStateOf("5.0") }
    var height by remember { mutableStateOf("3.2") }
    var doorWidth by remember { mutableStateOf("1.0") }
    var windows by remember { mutableStateOf("2") }
    var style by remember { mutableStateOf(MajlisStyle.OMANI_TRADITIONAL) }
    var seating by remember { mutableStateOf(SeatingLayout.PERIMETER) }
    var palette by remember { mutableStateOf(FinishPalette.SAND) }
    LaunchedEffect(savedDesign?.updatedAt) {
        savedDesign?.let { saved ->
            length = saved.lengthM.toString(); width = saved.widthM.toString(); height = saved.heightM.toString()
            doorWidth = saved.doorWidthM.toString(); windows = saved.windowCount.toString()
            style = MajlisStyle.values().firstOrNull { it.name == saved.style } ?: MajlisStyle.OMANI_TRADITIONAL
            seating = SeatingLayout.values().firstOrNull { it.name == saved.seating } ?: SeatingLayout.PERIMETER
            palette = FinishPalette.values().firstOrNull { it.name == saved.palette } ?: FinishPalette.SAND
        }
    }
    val room = InteriorRoom(length.toDoubleOrNull() ?: 0.0, width.toDoubleOrNull() ?: 0.0,
        height.toDoubleOrNull() ?: 0.0, doorWidth.toDoubleOrNull() ?: 0.0, windows.toIntOrNull() ?: -1, style, seating, palette)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("التصميم الداخلي للمجلس", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("أدخل أبعاد المجلس واختر الطراز وتوزيع الجلسات والألوان. المعاينة الحالية 2D مبدئية.", fontSize = 13.sp)
        Text("أبعاد المجلس", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignField("الطول بالمتر", length, { length = it }, Modifier.weight(1f))
            DesignField("العرض بالمتر", width, { width = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignField("الارتفاع بالمتر", height, { height = it }, Modifier.weight(1f))
            DesignField("عرض الباب", doorWidth, { doorWidth = it }, Modifier.weight(1f))
        }
        DesignField("عدد النوافذ", windows, { windows = it }, Modifier.fillMaxWidth())
        Text("طراز المجلس", fontWeight = FontWeight.Bold)
        DesignChoices(MajlisStyle.values().toList(), style, { style = it }) { it.title }
        Text("توزيع الجلسات", fontWeight = FontWeight.Bold)
        DesignChoices(SeatingLayout.values().toList(), seating, { seating = it }) { it.title }
        Text("الألوان والخامات", fontWeight = FontWeight.Bold)
        FinishPalette.values().forEach { option ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(64.dp, 28.dp)) {
                    val w = size.width / 3f
                    drawRect(readColor(option.wallHex), Offset.Zero, Size(w, size.height))
                    drawRect(readColor(option.floorHex), Offset(w, 0f), Size(w, size.height))
                    drawRect(readColor(option.accentHex), Offset(w * 2, 0f), Size(w, size.height))
                }
                Text(option.title, Modifier.weight(1f).padding(horizontal = 8.dp))
                RadioButton(selected = palette == option, onClick = { palette = option })
            }
        }
        Text("معاينة المخطط 2D", fontWeight = FontWeight.Bold)
        if (room.isValid) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(250.dp).background(Color(0xFFF8F5EF), RoundedCornerShape(8.dp))) {
                        val margin = 18f
                        val scale = minOf((size.width - margin * 2) / room.lengthM.toFloat(), (size.height - margin * 2) / room.widthM.toFloat())
                        val rw = room.lengthM.toFloat() * scale
                        val rh = room.widthM.toFloat() * scale
                        val x = (size.width - rw) / 2f
                        val y = (size.height - rh) / 2f
                        drawRect(Color(0xFF51483E), Offset(x, y), Size(rw, rh), style = Stroke(5f))
                        val sofa = readColor(palette.accentHex)
                        MajlisLayoutPlanner.seating(room, seating).forEach { element ->
                            drawRect(sofa,
                                Offset(x + element.xM.toFloat() * scale, y + element.yM.toFloat() * scale),
                                Size(element.widthM.toFloat() * scale, element.depthM.toFloat() * scale))
                        }
                        val door = room.doorWidthM.toFloat() * scale
                        drawLine(Color(0xFFF8F5EF), Offset(x, y + rh * 0.72f), Offset(x, y + rh * 0.72f + door), 8f)
                        if (room.windowCount > 0) {
                            val step = rw / (room.windowCount + 1)
                            repeat(room.windowCount) { i ->
                                val wx = x + step * (i + 1)
                                drawLine(Color(0xFF367B9B), Offset(wx - 12f, y), Offset(wx + 12f, y), 5f)
                            }
                        }
                    }
                    Text("مساحة الأرضية: " + areaText(room.floorAreaM2) + " م²")
                    Text("المحيط: " + areaText(room.perimeterM) + " م")
                    Text("الطراز: " + style.title + " — " + seating.title, fontSize = 12.sp)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ملخص المساحات", fontWeight = FontWeight.Bold)
                    Text("الأرضية: " + areaText(room.floorAreaM2) + " م²")
                    Text("الجدران الإجمالية: " + areaText(room.grossWallAreaM2) + " م²")
                    Text("صافي الجدران التقديري: " + areaText(room.netWallAreaM2()) + " م²")
                    Text("تقدير صافي الجدران يفترض بابًا بارتفاع 2.2 م ونافذة 1.2 م² لكل نافذة.", fontSize = 11.sp)
                }
            }
            Button(onClick = { onSaveDesign(room) }, modifier = Modifier.fillMaxWidth()) { Text("حفظ واعتماد التصميم للمشروع") }
        } else {
            Text("الأبعاد غير صحيحة. الطول والعرض 2–30 م، الارتفاع 2.2–8 م، وعرض الباب 0.6–2.5 م.", color = MaterialTheme.colorScheme.error)
        }
        Text("التصميم المحفوظ مرتبط بهذا المشروع ويستعيد أبعاده وخياراته عند إعادة فتحه. المعاينة ثنائية الأبعاد مفاهيمية وليست مخططًا تنفيذيًا أو تصميم 3D.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DesignField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier, singleLine = true)
}

@Composable
private fun <T> DesignChoices(items: List<T>, selected: T, onSelect: (T) -> Unit, title: (T) -> String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEach { item -> FilterChip(selected == item, { onSelect(item) }, label = { Text(title(item)) }) }
    }
}

private fun readColor(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
private fun areaText(value: Double): String = String.format(Locale.US, "%.2f", value)
