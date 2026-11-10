package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "projects", indices = [Index("createdAt"), Index("status")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val clientName: String = "",
    val clientPhone: String = "",
    val location: String = "",
    val projectType: String = "مجلس",
    val status: String = "جديد",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_surveys", indices = [Index("projectId")])
data class SiteSurveyEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val spaceName: String,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val openingsAreaM2: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val floorAreaM2: Double get() = lengthM * widthM
    val wallAreaM2: Double get() = maxOf(0.0, 2.0 * (lengthM + widthM) * heightM - openingsAreaM2)
}

@Entity(tableName = "boq_items", indices = [Index("projectId")])
data class BoqItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val description: String,
    val unit: String,
    val quantity: Double,
    /** Customer selling rate per net unit; retained as the legacy database column. */
    val unitRate: Double,
    val category: String = "عام",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0.0") val wastePercent: Double = 0.0,
    @ColumnInfo(defaultValue = "1.0") val purchaseIncrement: Double = 1.0,
    @ColumnInfo(defaultValue = "0.0") val materialUnitRate: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val laborUnitRate: Double = 0.0
) {
    val total: Double get() = quantity * unitRate
}

@Entity(tableName = "project_tasks", indices = [Index("projectId"), Index("status")])
data class ProjectTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val details: String = "",
    val status: String = "لم يبدأ",
    val dueAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "drawing_notes", indices = [Index("projectId"), Index("layer")])
data class DrawingNoteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val layer: String,
    val title: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
