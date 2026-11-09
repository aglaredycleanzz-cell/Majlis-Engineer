package com.majlisengineer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialsDao {
    @Query("SELECT * FROM material_categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<MaterialCategoryEntity>>

    @Query("SELECT * FROM materials ORDER BY nameAr")
    fun observeMaterials(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE categoryId=:categoryId ORDER BY nameAr")
    fun observeByCategory(categoryId: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE normalizedSearchText LIKE '%' || :query || '%' ORDER BY nameAr")
    fun search(query: String): Flow<List<MaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<MaterialCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(items: List<MaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: MaterialImageEntity)

    @Query("SELECT COUNT(*) FROM material_categories")
    suspend fun categoryCount(): Int

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id=:projectId LIMIT 1")
    fun observeProject(projectId: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM site_surveys WHERE projectId=:projectId ORDER BY createdAt DESC")
    fun observeSurveys(projectId: String): Flow<List<SiteSurveyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: SiteSurveyEntity)

    @Query("DELETE FROM site_surveys WHERE id=:id")
    suspend fun deleteSurvey(id: String)

    @Query("SELECT * FROM boq_items WHERE projectId=:projectId ORDER BY createdAt")
    fun observeBoqItems(projectId: String): Flow<List<BoqItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoqItem(item: BoqItemEntity)

    @Query("DELETE FROM boq_items WHERE id=:id")
    suspend fun deleteBoqItem(id: String)

    @Query("SELECT * FROM project_tasks WHERE projectId=:projectId ORDER BY createdAt DESC")
    fun observeTasks(projectId: String): Flow<List<ProjectTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ProjectTaskEntity)

    @Query("UPDATE project_tasks SET status=:status WHERE id=:id")
    suspend fun updateTaskStatus(id: String, status: String)

    @Query("DELETE FROM project_tasks WHERE id=:id")
    suspend fun deleteTask(id: String)

    @Query("SELECT * FROM drawing_notes WHERE projectId=:projectId ORDER BY createdAt DESC")
    fun observeDrawingNotes(projectId: String): Flow<List<DrawingNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawingNote(note: DrawingNoteEntity)

    @Query("DELETE FROM drawing_notes WHERE id=:id")
    suspend fun deleteDrawingNote(id: String)
}
