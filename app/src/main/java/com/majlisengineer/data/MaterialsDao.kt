package com.majlisengineer.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface MaterialsDao {
 @Query("SELECT * FROM material_categories ORDER BY sortOrder") fun observeCategories():Flow<List<MaterialCategoryEntity>>
 @Query("SELECT * FROM materials ORDER BY nameAr") fun observeMaterials():Flow<List<MaterialEntity>>
 @Query("SELECT * FROM materials WHERE categoryId=:categoryId ORDER BY nameAr") fun observeByCategory(categoryId:String):Flow<List<MaterialEntity>>
 @Query("SELECT * FROM materials WHERE normalizedSearchText LIKE '%' || :query || '%' ORDER BY nameAr") fun search(query:String):Flow<List<MaterialEntity>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertCategories(items:List<MaterialCategoryEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertMaterials(items:List<MaterialEntity>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun insertImage(image:MaterialImageEntity)
 @Query("SELECT COUNT(*) FROM material_categories") suspend fun categoryCount():Int
}
