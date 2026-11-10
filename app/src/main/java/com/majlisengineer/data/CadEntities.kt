package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cad_layers", indices = [Index("projectId")])
data class CadLayerEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val colorHex: String = "#285C50",
    val visible: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "cad_entities", indices = [Index("projectId"), Index("layerId")])
data class CadDrawingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val layerId: String,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val label: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
