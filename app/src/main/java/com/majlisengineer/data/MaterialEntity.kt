package com.majlisengineer.data
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
@Entity(tableName="materials",indices=[Index("categoryId"),Index("normalizedSearchText")])
data class MaterialEntity(
 @PrimaryKey val id:String,val categoryId:String,val nameAr:String,val nameEn:String="",
 val subcategory:String="",val shortNote:String="",val aliases:String="",val normalizedSearchText:String,
 val createdAt:Long=System.currentTimeMillis(),
 @ColumnInfo(defaultValue = "'قطعة'") val baseUnit:String="قطعة",
 @ColumnInfo(defaultValue = "''") val brand:String="",
 @ColumnInfo(defaultValue = "0.0") val baseUnitPriceOmr:Double=0.0,
 @ColumnInfo(defaultValue = "0") val basePriceUpdatedAt:Long=0
)
