package com.majlisengineer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class MaterialCatalogSeedTest {
    @Test
    fun bundledCatalogContainsBroadOfflineMasterDataForEveryCategory() {
        val roots = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.toList()
        val asset = roots.flatMap { root ->
            listOf(
                File(root, "src/main/assets/${MaterialCatalogSeed.ASSET_NAME}"),
                File(root, "app/src/main/assets/${MaterialCatalogSeed.ASSET_NAME}")
            )
        }.firstOrNull { it.isFile } ?: error("Bundled material catalog asset was not found")

        val materials = asset.inputStream().use(MaterialCatalogSeed::load)
        assertTrue("Expected a broad starter catalog, got ${materials.size}", materials.size >= 300)
        assertEquals(materials.size, materials.map { it.id }.distinct().size)
        assertEquals(MaterialCategorySeed.categories.map { it.id }.toSet(), materials.map { it.categoryId }.toSet())
        assertTrue(materials.all { it.id.startsWith("builtin-") })
        assertTrue(materials.all { it.nameAr.isNotBlank() && it.nameEn.isNotBlank() && it.baseUnit.isNotBlank() })
        assertTrue(materials.all { it.baseUnitPriceOmr == 0.0 && it.basePriceUpdatedAt == 0L && it.brand.isBlank() })
        assertTrue(materials.any { it.normalizedSearchText.contains("ppr") })
        assertTrue(materials.any { it.normalizedSearchText.contains(MaterialSearchNormalizer.normalize("سبوت لايت")) })
    }

    @Test
    fun parserRejectsUnknownCategoriesAndDuplicateIds() {
        val unknownCategory = "${MaterialCatalogSeed.HEADER}\nbuiltin-test|unknown|مادة|Material|اختبار|قطعة|test"
        assertTrue(runCatching { MaterialCatalogSeed.parse(StringReader(unknownCategory)) }.isFailure)

        val duplicateRows = "${MaterialCatalogSeed.HEADER}\n" +
            "builtin-test|plumbing|مادة أولى|First item|اختبار|قطعة|first\n" +
            "builtin-test|plumbing|مادة ثانية|Second item|اختبار|قطعة|second"
        val error = runCatching { MaterialCatalogSeed.parse(StringReader(duplicateRows)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(error?.message.isNullOrBlank())
    }
}
