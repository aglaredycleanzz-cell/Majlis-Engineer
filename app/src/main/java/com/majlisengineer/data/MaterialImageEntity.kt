package com.majlisengineer.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName="material_images",indices=[Index("materialId")])
data class MaterialImageEntity(@PrimaryKey val id:String,val materialId:String,val localPath:String,val caption:String="",val sortOrder:Int=0)
