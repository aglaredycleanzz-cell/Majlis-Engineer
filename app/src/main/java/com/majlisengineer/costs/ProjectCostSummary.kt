package com.majlisengineer.costs

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * ملخص تكلفة المشروع: يجمع نتائج [CostCalculator] لبنود الحصر ويضيف النقل والمصاريف
 * الإضافية والخصومات. محرك نقي لا يعتمد على الواجهة أو Room أو CAD. كل المبالغ بالريال
 * العماني إلى ثلاث منازل عشرية بتقريب HALF_UP.
 */
data class ProjectCostLine(
    val breakdown: CostBreakdown,
    /** true فقط إذا أدخل المستخدم سعر مادة أو عمالة للبند؛ لا تُفترض تكلفة صفرية للبنود غير المسعرة. */
    val isCosted: Boolean
)

data class ProjectCostAdjustments(
    val transportOmr: Double = 0.0,
    val additionalExpensesOmr: Double = 0.0,
    /** نسبة خصم من إجمالي البيع، بين 0 و100، تُطبّق أولًا. */
    val discountPercent: Double = 0.0,
    /** خصم مبلغ ثابت يُطبّق بعد خصم النسبة. */
    val discountFixedOmr: Double = 0.0
)

data class ProjectCostSummary(
    val lineCount: Int,
    val uncostedLineCount: Int,
    val materialCostOmr: BigDecimal,
    val laborCostOmr: BigDecimal,
    val itemsCostOmr: BigDecimal,
    val transportOmr: BigDecimal,
    val additionalExpensesOmr: BigDecimal,
    val estimatedCostOmr: BigDecimal,
    val salesSubtotalOmr: BigDecimal,
    val percentDiscountOmr: BigDecimal,
    val fixedDiscountOmr: BigDecimal,
    val totalDiscountOmr: BigDecimal,
    val netSalesOmr: BigDecimal,
    /** null إذا لا توجد بنود أو إذا بقي أي بند دون تسعير تكلفة؛ لا يُعرض ربح جزئي على أنه كامل. */
    val expectedProfitOmr: BigDecimal?,
    /** null إذا الربح غير معرّف أو كان صافي البيع صفرًا. */
    val expectedMarginPercent: BigDecimal?
)

object ProjectCostSummaryCalculator {
    private val ZERO = BigDecimal.ZERO
    private val ONE_HUNDRED = BigDecimal("100")

    fun summarize(
        lines: List<ProjectCostLine>,
        adjustments: ProjectCostAdjustments = ProjectCostAdjustments()
    ): ProjectCostSummary {
        validate(adjustments)

        val costed = lines.filter { it.isCosted }
        val material = money(costed.fold(ZERO) { acc, l -> acc.add(l.breakdown.materialCostOmr) })
        val labor = money(costed.fold(ZERO) { acc, l -> acc.add(l.breakdown.laborCostOmr) })
        val itemsCost = money(material.add(labor))
        val transport = money(BigDecimal.valueOf(adjustments.transportOmr))
        val extras = money(BigDecimal.valueOf(adjustments.additionalExpensesOmr))
        val estimatedCost = money(itemsCost.add(transport).add(extras))

        val salesSubtotal = money(lines.fold(ZERO) { acc, l -> acc.add(l.breakdown.salesAmountOmr) })
        val percentDiscount = money(
            salesSubtotal.multiply(BigDecimal.valueOf(adjustments.discountPercent))
                .divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP)
        )
        val afterPercent = salesSubtotal.subtract(percentDiscount)
        val fixedDiscount = money(BigDecimal.valueOf(adjustments.discountFixedOmr))
        require(fixedDiscount <= afterPercent) { "لا يمكن أن يتجاوز الخصم الثابت إجمالي البيع بعد خصم النسبة" }
        val totalDiscount = money(percentDiscount.add(fixedDiscount))
        val netSales = money(salesSubtotal.subtract(totalDiscount))

        val uncosted = lines.count { !it.isCosted }
        val profitDefined = lines.isNotEmpty() && uncosted == 0
        val profit = if (profitDefined) money(netSales.subtract(estimatedCost)) else null
        val margin = if (profit == null || netSales.compareTo(ZERO) == 0) null
        else profit.multiply(ONE_HUNDRED).divide(netSales, 3, RoundingMode.HALF_UP)

        return ProjectCostSummary(
            lineCount = lines.size,
            uncostedLineCount = uncosted,
            materialCostOmr = material,
            laborCostOmr = labor,
            itemsCostOmr = itemsCost,
            transportOmr = transport,
            additionalExpensesOmr = extras,
            estimatedCostOmr = estimatedCost,
            salesSubtotalOmr = salesSubtotal,
            percentDiscountOmr = percentDiscount,
            fixedDiscountOmr = fixedDiscount,
            totalDiscountOmr = totalDiscount,
            netSalesOmr = netSales,
            expectedProfitOmr = profit,
            expectedMarginPercent = margin
        )
    }

    private fun validate(a: ProjectCostAdjustments) {
        requireFinite("تكلفة النقل", a.transportOmr)
        requireFinite("المصاريف الإضافية", a.additionalExpensesOmr)
        requireFinite("نسبة الخصم", a.discountPercent)
        requireFinite("الخصم الثابت", a.discountFixedOmr)
        require(a.transportOmr >= 0.0 && a.additionalExpensesOmr >= 0.0 && a.discountFixedOmr >= 0.0) {
            "لا يمكن أن تكون المبالغ سالبة"
        }
        require(a.discountPercent in 0.0..100.0) { "يجب أن تكون نسبة الخصم بين 0 و100" }
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(3, RoundingMode.HALF_UP)

    private fun requireFinite(label: String, value: Double) {
        require(value.isFinite()) { "$label يجب أن تكون قيمة رقمية صالحة" }
    }
}
