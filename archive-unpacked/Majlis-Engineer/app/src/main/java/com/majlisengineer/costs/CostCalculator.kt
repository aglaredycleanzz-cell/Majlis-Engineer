package com.majlisengineer.costs

import java.math.BigDecimal
import java.math.RoundingMode

/** Pure calculation engine; UI and CAD are deliberately not dependencies. */
data class CostBreakdown(
    val netQuantity: BigDecimal,
    val targetPurchaseQuantity: BigDecimal,
    val purchaseQuantity: BigDecimal,
    val wasteQuantity: BigDecimal,
    val roundingSurplus: BigDecimal,
    val materialCostOmr: BigDecimal,
    val laborCostOmr: BigDecimal,
    val totalCostOmr: BigDecimal,
    val salesAmountOmr: BigDecimal,
    val expectedProfitOmr: BigDecimal,
    val expectedMarginPercent: BigDecimal?
)

object CostCalculator {
    private val ZERO = BigDecimal.ZERO
    private val ONE_HUNDRED = BigDecimal("100")

    /**
     * Calculates procurement quantity and OMR values for one BOQ item.
     * saleUnitRateOmr is a selling rate per net unit, independent of project costs.
     */
    fun calculate(
        netQuantity: Double,
        wastePercent: Double,
        purchaseIncrement: Double,
        materialUnitRateOmr: Double,
        laborUnitRateOmr: Double,
        saleUnitRateOmr: Double
    ): CostBreakdown {
        requireFinite("الكمية الصافية", netQuantity)
        requireFinite("نسبة الهدر", wastePercent)
        requireFinite("وحدة الشراء", purchaseIncrement)
        requireFinite("سعر المادة", materialUnitRateOmr)
        requireFinite("سعر العمالة", laborUnitRateOmr)
        requireFinite("سعر البيع", saleUnitRateOmr)
        require(netQuantity > 0.0) { "يجب أن تكون الكمية الصافية أكبر من صفر" }
        require(wastePercent >= 0.0) { "لا يمكن أن تكون نسبة الهدر سالبة" }
        require(purchaseIncrement > 0.0) { "يجب أن تكون وحدة الشراء أكبر من صفر" }
        require(materialUnitRateOmr >= 0.0 && laborUnitRateOmr >= 0.0 && saleUnitRateOmr >= 0.0) {
            "لا يمكن أن تكون الأسعار سالبة"
        }

        val net = BigDecimal.valueOf(netQuantity)
        val increment = BigDecimal.valueOf(purchaseIncrement)
        val target = net.multiply(
            BigDecimal.ONE.add(BigDecimal.valueOf(wastePercent).divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP))
        )
        val purchase = target.divide(increment, 0, RoundingMode.CEILING).multiply(increment)
        val waste = target.subtract(net)
        val surplus = purchase.subtract(target)
        val materialCost = money(purchase.multiply(BigDecimal.valueOf(materialUnitRateOmr)))
        val laborCost = money(net.multiply(BigDecimal.valueOf(laborUnitRateOmr)))
        val totalCost = money(materialCost.add(laborCost))
        val sales = money(net.multiply(BigDecimal.valueOf(saleUnitRateOmr)))
        val profit = money(sales.subtract(totalCost))
        val margin = if (sales.compareTo(ZERO) == 0) null
        else profit.multiply(ONE_HUNDRED).divide(sales, 3, RoundingMode.HALF_UP)

        return CostBreakdown(
            netQuantity = net.stripTrailingZeros(),
            targetPurchaseQuantity = target.stripTrailingZeros(),
            purchaseQuantity = purchase.stripTrailingZeros(),
            wasteQuantity = waste.stripTrailingZeros(),
            roundingSurplus = surplus.stripTrailingZeros(),
            materialCostOmr = materialCost,
            laborCostOmr = laborCost,
            totalCostOmr = totalCost,
            salesAmountOmr = sales,
            expectedProfitOmr = profit,
            expectedMarginPercent = margin
        )
    }

    private fun money(value: BigDecimal): BigDecimal = value.setScale(3, RoundingMode.HALF_UP)

    private fun requireFinite(label: String, value: Double) {
        require(value.isFinite()) { "$label يجب أن تكون قيمة رقمية صالحة" }
    }
}
