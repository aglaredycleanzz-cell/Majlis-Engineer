package com.majlisengineer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.majlisengineer.data.ProjectCostSettingsEntity
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
internal fun ProjectCostSettingsDialog(
    current: ProjectCostSettingsEntity,
    salesSubtotalOmr: Double,
    onDismiss: () -> Unit,
    onSave: (transport: Double, expenses: Double, discountPercent: Double, discountFixed: Double) -> Unit
) {
    var transport by remember(current.updatedAt) { mutableStateOf(current.transportOmr.toString()) }
    var expenses by remember(current.updatedAt) { mutableStateOf(current.additionalExpensesOmr.toString()) }
    var percent by remember(current.updatedAt) { mutableStateOf(current.discountPercent.toString()) }
    var fixed by remember(current.updatedAt) { mutableStateOf(current.discountFixedOmr.toString()) }
    val t = transport.toDoubleOrNull()
    val e = expenses.toDoubleOrNull()
    val p = percent.toDoubleOrNull()
    val f = fixed.toDoubleOrNull()
    val maxFixed = if (salesSubtotalOmr.isFinite() && salesSubtotalOmr >= 0.0 && p != null && p in 0.0..100.0) {
        val percentDiscount = BigDecimal.valueOf(salesSubtotalOmr)
            .multiply(BigDecimal.valueOf(p)).divide(BigDecimal("100"), 3, RoundingMode.HALF_UP)
        BigDecimal.valueOf(salesSubtotalOmr).setScale(3, RoundingMode.HALF_UP)
            .subtract(percentDiscount).toDouble()
    } else -1.0
    val valid = listOf(t, e, p, f).all { it != null && it.isFinite() } &&
        (t ?: -1.0) >= 0.0 && (e ?: -1.0) >= 0.0 &&
        (p ?: -1.0) in 0.0..100.0 && (f ?: -1.0) >= 0.0 &&
        (f ?: Double.POSITIVE_INFINITY) <= maxFixed + 0.000000001

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات التكلفة للمشروع") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(transport, { transport = it }, label = { Text("النقل (ر.ع)") }, singleLine = true)
                OutlinedTextField(expenses, { expenses = it }, label = { Text("المصاريف الإضافية (ر.ع)") }, singleLine = true)
                OutlinedTextField(percent, { percent = it }, label = { Text("خصم نسبة مئوية (0–100)") }, singleLine = true)
                OutlinedTextField(fixed, { fixed = it }, label = { Text("خصم ثابت بعد النسبة (ر.ع)") }, singleLine = true)
                if (f != null && maxFixed >= 0.0 && f > maxFixed + 0.000000001) {
                    Text("الخصم الثابت يتجاوز صافي البيع بعد الخصم النسبي (${String.format(java.util.Locale.US, "%.3f", maxFixed)} ر.ع).")
                }
                if (!valid) Text("تحقق من أن جميع القيم أرقام غير سالبة، وأن نسبة الخصم لا تتجاوز 100%.")
                Text("تحفظ هذه الإعدادات لهذا المشروع وحده. لا تُحتسب الأرباح إذا بقي بند بلا تكلفة أو تعذر حسابه.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(t!!, e!!, p!!, f!!) }) { Text("حفظ الإعدادات") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
