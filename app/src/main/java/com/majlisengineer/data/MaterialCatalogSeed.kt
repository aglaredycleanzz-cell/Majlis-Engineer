package com.majlisengineer.data

import java.io.InputStream
import java.io.Reader

/** Loads the bundled, offline, generic material master catalog. */
object MaterialCatalogSeed {
    const val ASSET_NAME = "materials_catalog_v1.psv"
    const val HEADER = "id|categoryId|nameAr|nameEn|subcategory|baseUnit|aliases"
    private const val UNKNOWN_PRICE_NOTE = "صنف عام بلا ماركة أو سعر مورد. سجل عرض مورد وتاريخه عند التسعير."

    fun load(input: InputStream): List<MaterialEntity> =
        input.bufferedReader(Charsets.UTF_8).use { reader -> parse(reader) }

    fun parse(reader: Reader): List<MaterialEntity> {
        val lines = reader.readLines()
        require(lines.firstOrNull() == HEADER) { "Unexpected material catalog header" }

        val validCategoryIds = MaterialCategorySeed.categories.map { it.id }.toSet()
        val materials = lines.drop(1).mapIndexedNotNull { index, line ->
            if (line.isBlank()) return@mapIndexedNotNull null

            val fields = line.split('|', limit = 7)
            require(fields.size == 7) { "Invalid material catalog row ${index + 2}: expected 7 fields" }
            val id = fields[0].trim()
            val categoryId = fields[1].trim()
            val nameAr = fields[2].trim()
            val nameEn = fields[3].trim()
            val subcategory = fields[4].trim()
            val unit = fields[5].trim()
            val aliases = fields[6].trim()
            require(id.startsWith("builtin-")) { "Catalog row ${index + 2} has a non-stable built-in id" }
            require(categoryId in validCategoryIds) { "Catalog row ${index + 2} has unknown category $categoryId" }
            require(nameAr.isNotBlank() && nameEn.isNotBlank() && unit.isNotBlank()) {
                "Catalog row ${index + 2} is missing a name or unit"
            }

            MaterialEntity(
                id = id,
                categoryId = categoryId,
                nameAr = nameAr,
                nameEn = nameEn,
                subcategory = subcategory,
                shortNote = UNKNOWN_PRICE_NOTE,
                aliases = aliases,
                normalizedSearchText = MaterialSearchNormalizer.normalize(
                    listOf(nameAr, nameEn, subcategory, aliases).joinToString(" ")
                ),
                baseUnit = unit,
                baseUnitPriceOmr = 0.0,
                basePriceUpdatedAt = 0L
            )
        }

        require(materials.isNotEmpty()) { "Material catalog is empty" }
        require(materials.map { it.id }.distinct().size == materials.size) { "Material catalog contains duplicate ids" }
        return materials
    }
}
