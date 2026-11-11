package com.majlisengineer.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "material_categories")
data class MaterialCategoryEntity(@PrimaryKey val id:String,val nameAr:String,val nameEn:String,val sortOrder:Int)
