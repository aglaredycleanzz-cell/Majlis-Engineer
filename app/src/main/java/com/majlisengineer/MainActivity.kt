package com.majlisengineer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProofHomeScreen()
                }
            }
        }
    }
}

private data class ProofArea(
    val title: String,
    val description: String,
    val status: String = "لم يُختبر بعد"
)

@Composable
private fun ProofHomeScreen() {
    val areas = listOf(
        ProofArea("الرسم الهندسي CAD", "إنشاء العناصر وتعديلها وحفظ المخطط وإعادة فتحه"),
        ProofArea("ملفات DXF", "استيراد المخططات وتعديل العناصر المدعومة وتصديرها"),
        ProofArea("تحويل DWG", "اختبار التحويل إلى DXF ومقارنة المقياس والطبقات"),
        ProofArea("العرض ثلاثي الأبعاد", "قياس الأداء والذاكرة والحرارة على الهاتف")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "مهندس المجلس",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "بيئة الاختبارات الهندسية الأولية",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
        items(areas.size) { index ->
            val area = areas[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(area.title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    Text(
                        area.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Text(
                        area.status,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
