package com.majlisengineer.costs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class ProjectCostSummaryTest {
    private fun line(net: Double, mat: Double, labor: Double, sale: Double) = ProjectCostLine(
        breakdown = CostCalculator.calculate(net, 0.0, 1.0, mat, labor, sale),
        isCosted = mat > 0.0 || labor > 0.0
    )

    @Test fun sumsItemsThenAddsTransportExtrasAndAppliesDiscounts() {
        val first = ProjectCostLine(CostCalculator.calculate(10.0, 10.0, 1.0, 2.345, 0.5, 4.0), true)
        val second = line(2.0, 3.0, 1.0, 8.0)
        val s = ProjectCostSummaryCalculator.summarize(
            listOf(first, second),
            ProjectCostAdjustments(transportOmr = 5.0, additionalExpensesOmr = 1.5, discountPercent = 5.0, discountFixedOmr = 0.2)
        )

        assertDecimal("31.795", s.materialCostOmr)
        assertDecimal("7.000", s.laborCostOmr)
        assertDecimal("38.795", s.itemsCostOmr)
        assertDecimal("45.295", s.estimatedCostOmr)
        assertDecimal("56.000", s.salesSubtotalOmr)
        assertDecimal("2.800", s.percentDiscountOmr)
        assertDecimal("0.200", s.fixedDiscountOmr)
        assertDecimal("3.000", s.totalDiscountOmr)
        assertDecimal("53.000", s.netSalesOmr)
        assertDecimal("7.705", s.expectedProfitOmr!!)
        assertDecimal("14.538", s.expectedMarginPercent!!)
        assertEquals(0, s.uncostedLineCount)
    }

    @Test fun uncostedLinesAreNeverAssumedFreeAndProfitIsUndefined() {
        val costed = line(2.0, 3.0, 1.0, 8.0)
        val uncosted = line(5.0, 0.0, 0.0, 10.0)
        val s = ProjectCostSummaryCalculator.summarize(listOf(costed, uncosted))

        assertEquals(1, s.uncostedLineCount)
        assertDecimal("8.000", s.itemsCostOmr)
        assertDecimal("66.000", s.salesSubtotalOmr)
        assertNull(s.expectedProfitOmr)
        assertNull(s.expectedMarginPercent)
    }

    @Test fun emptyProjectHasZeroTotalsAndNoProfit() {
        val s = ProjectCostSummaryCalculator.summarize(emptyList(), ProjectCostAdjustments(transportOmr = 2.0))
        assertDecimal("2.000", s.estimatedCostOmr)
        assertDecimal("0.000", s.netSalesOmr)
        assertNull(s.expectedProfitOmr)
        assertNull(s.expectedMarginPercent)
    }

    @Test fun percentDiscountRoundsHalfUpToThreeDecimals() {
        val s = ProjectCostSummaryCalculator.summarize(
            listOf(ProjectCostLine(CostCalculator.calculate(1.0, 0.0, 1.0, 0.001, 0.0, 0.01), true)),
            ProjectCostAdjustments(discountPercent = 5.0)
        )
        assertDecimal("0.010", s.salesSubtotalOmr)
        assertDecimal("0.001", s.percentDiscountOmr)
        assertDecimal("0.009", s.netSalesOmr)
    }

    @Test fun zeroNetSalesHasNoMargin() {
        val s = ProjectCostSummaryCalculator.summarize(
            listOf(line(2.0, 3.0, 1.0, 8.0)),
            ProjectCostAdjustments(discountPercent = 100.0)
        )
        assertDecimal("0.000", s.netSalesOmr)
        assertDecimal("-8.000", s.expectedProfitOmr!!)
        assertNull(s.expectedMarginPercent)
    }

    @Test fun rejectsInvalidAdjustments() {
        val lines = listOf(line(2.0, 3.0, 1.0, 8.0))
        fun bad(a: ProjectCostAdjustments) =
            assertThrows(IllegalArgumentException::class.java) { ProjectCostSummaryCalculator.summarize(lines, a) }
        bad(ProjectCostAdjustments(transportOmr = -0.001))
        bad(ProjectCostAdjustments(additionalExpensesOmr = -1.0))
        bad(ProjectCostAdjustments(discountPercent = 100.5))
        bad(ProjectCostAdjustments(discountPercent = -1.0))
        bad(ProjectCostAdjustments(discountFixedOmr = 16.001))
        bad(ProjectCostAdjustments(transportOmr = Double.NaN))
        bad(ProjectCostAdjustments(discountPercent = 50.0, discountFixedOmr = 8.001))
        assertFalse(lines.isEmpty())
    }

    @Test fun fixedDiscountCannotExceedSalesAfterPercentageDiscount() {
        val lines = listOf(line(1.0, 0.1, 0.0, 10.0))
        val allowed = ProjectCostSummaryCalculator.summarize(
            lines, ProjectCostAdjustments(discountPercent = 10.0, discountFixedOmr = 9.0)
        )
        assertDecimal("0.000", allowed.netSalesOmr)
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCostSummaryCalculator.summarize(
                lines, ProjectCostAdjustments(discountPercent = 10.0, discountFixedOmr = 9.001)
            )
        }
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
