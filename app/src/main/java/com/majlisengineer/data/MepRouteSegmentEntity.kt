package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mep_route_segments",
    indices = [Index("projectId"), Index("discipline")]
)
data class MepRouteSegmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val discipline: String,
    val startX: Double,
    val startY: Double,
    val startZ: Double,
    val endX: Double,
    val endY: Double,
    val endZ: Double,
    val nominalDiameterMm: Double,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
