package com.majlisengineer.costs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class CostCalculatorTest {
    @Test fun calculatesWastePurchaseMaterialLaborSalesAndProfit() {
        val result = CostCalculator.calculate(
            netQuantity = 10.0,
            wastePercent = 10.0,
            purchaseIncrement = 1.0,
            materialUnitRateOmr = 2.345,
            laborUnitRateOmr = 0.5,
            saleUnitRateOmr = 4.0
        )

        assertDecimal("10", result.netQuantity)
        assertDecimal("11", result.targetPurchaseQuantity)
        assertDecimal("11", result.purchaseQuantity)
        assertDecimal("1", result.wasteQuantity)
        assertDecimal("0", result.roundingSurplus)
        assertDecimal("25.795", result.materialCostOmr)
        assertDecimal("5.000", result.laborCostOmr)
        assertDecimal("30.795", result.totalCostOmr)
        assertDecimal("40.000", result.salesAmountOmr)
        assertDecimal("9.205", result.expectedProfitOmr)
        assertDecimal("23.013", result.expectedMarginPercent!!)
    }

    @Test fun roundsPurchaseUpToActualSalesIncrement() {
        val result = CostCalculator.calculate(10.01, 0.0, 0.5, 1.0, 0.0, 1.5)
        assertDecimal("10.01", result.targetPurchaseQuantity)
        assertDecimal("10.5", result.purchaseQuantity)
        assertDecimal("0.49", result.roundingSurplus)
    }

    @Test fun roundsCurrencyToThreeDecimalsUsingHalfUp() {
        val result = CostCalculator.calculate(1.0, 0.0, 0.01, 0.0005, 0.0, 1.0005)
        assertDecimal("0.001", result.materialCostOmr)
        assertDecimal("1.001", result.salesAmountOmr)
    }

    @Test fun zeroSalesHasNoDefinedProfitMargin() {
        val result = CostCalculator.calculate(2.0, 0.0, 1.0, 0.0, 0.0, 0.0)
        assertDecimal("0.000", result.expectedProfitOmr)
        assertNull(result.expectedMarginPercent)
    }

    @Test fun rejectsInvalidQuantitiesPercentagesStepsAndPrices() {
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.calculate(0.0, 0.0, 1.0, 0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.calculate(1.0, -1.0, 1.0, 0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.calculate(1.0, 0.0, 0.0, 0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.calculate(1.0, 0.0, 1.0, -0.01, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { CostCalculator.calculate(Double.NaN, 0.0, 1.0, 0.0, 0.0, 0.0) }
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
