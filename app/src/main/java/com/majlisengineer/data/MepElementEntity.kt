package com.majlisengineer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mep_elements",
    indices = [Index("projectId"), Index(value = ["projectId", "surveyId"]), Index(value = ["projectId", "surveyId", "system"])]
)
data class MepElementEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val surveyId: String,
    /** Stored enum name from MepSystem. */
    val system: String,
    /** Stored enum name from MepElementType. */
    val elementType: String,
    /** MepGeometryCodec format; coordinates are in metres relative to the room origin. */
    val geometryData: String,
    val nominalDiameterMm: Double,
    val material: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)
