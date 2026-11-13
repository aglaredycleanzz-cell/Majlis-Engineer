package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** إعدادات مالية محفوظة لكل مشروع، منفصلة عن سعر المواد الأساسي. */
@Entity(tableName = "project_cost_settings")
data class ProjectCostSettingsEntity(
    @PrimaryKey val projectId: String,
    val transportOmr: Double = 0.0,
    val additionalExpensesOmr: Double = 0.0,
    val discountPercent: Double = 0.0,
    val discountFixedOmr: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

/** آخر مواصفات تصميم داخلي معتمدة للمشروع؛ الأبعاد بالمتر. */
@Entity(tableName = "interior_designs")
data class InteriorDesignEntity(
    @PrimaryKey val projectId: String,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val doorWidthM: Double,
    val windowCount: Int,
    val style: String,
    val seating: String,
    val palette: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/** سجل مرجعي للملف الأصلي المحفوظ داخل مساحة التطبيق الخاصة قبل استيراد عناصره. */
@Entity(tableName = "dxf_import_records", indices = [Index("projectId")])
data class DxfImportRecordEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val originalFileName: String,
    val originalPath: String,
    val unitsCode: Int,
    val importedEntityCount: Int,
    val omittedEntityCount: Int,
    val conversionNotes: String = "",
    val importedAt: Long = System.currentTimeMillis()
)
