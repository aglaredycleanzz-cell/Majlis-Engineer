package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers", indices = [Index("name")])
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val email: String = "",
    val location: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "material_supplier_offers",
    indices = [Index("materialId"), Index("supplierId"), Index(value = ["materialId", "supplierId", "updatedAt"])]
)
data class MaterialSupplierOfferEntity(
    @PrimaryKey val id: String,
    val materialId: String,
    val supplierId: String,
    val brand: String = "",
    val unit: String,
    val unitPriceOmr: Double,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "project_materials",
    indices = [Index("projectId"), Index("materialId"), Index(value = ["projectId", "materialId"], unique = true)]
)
data class ProjectMaterialEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val materialId: String,
    val unitSnapshot: String,
    val requiredQuantity: Double,
    val usedQuantity: Double = 0.0,
    /** Null means use the material's base price; a value is scoped to this project only. */
    val projectUnitPriceOverrideOmr: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val remainingQuantity: Double get() = requiredQuantity - usedQuantity
}
