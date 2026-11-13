package com.majlisengineer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialsDao {
    @Query("SELECT * FROM material_categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<MaterialCategoryEntity>>
    @Query("SELECT * FROM materials ORDER BY nameAr")
    fun observeMaterials(): Flow<List<MaterialEntity>>
    @Update
    suspend fun updateMaterial(material: MaterialEntity)
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

    @Query("SELECT * FROM suppliers ORDER BY name")
    fun observeSuppliers(): Flow<List<SupplierEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity)
    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)
    @Query("SELECT * FROM material_supplier_offers ORDER BY updatedAt DESC")
    fun observeSupplierOffers(): Flow<List<MaterialSupplierOfferEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplierOffer(offer: MaterialSupplierOfferEntity)
    @Update
    suspend fun updateSupplierOffer(offer: MaterialSupplierOfferEntity)
    @Query("DELETE FROM material_supplier_offers WHERE id=:id")
    suspend fun deleteSupplierOffer(id: String)

    @Query("SELECT * FROM project_materials WHERE projectId=:projectId ORDER BY createdAt")
    fun observeProjectMaterials(projectId: String): Flow<List<ProjectMaterialEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjectMaterial(item: ProjectMaterialEntity): Long
    @Update
    suspend fun updateProjectMaterial(item: ProjectMaterialEntity)
    @Query("DELETE FROM project_materials WHERE id=:id")
    suspend fun deleteProjectMaterial(id: String)

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
    @Update
    suspend fun updateSurvey(survey: SiteSurveyEntity)
    @Query("DELETE FROM site_surveys WHERE id=:id")
    suspend fun deleteSurvey(id: String)

    @Query("SELECT * FROM boq_items WHERE projectId=:projectId ORDER BY createdAt")
    fun observeBoqItems(projectId: String): Flow<List<BoqItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoqItem(item: BoqItemEntity)
    @Update
    suspend fun updateBoqItem(item: BoqItemEntity)
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

    @Query("SELECT * FROM project_cost_settings WHERE projectId=:projectId LIMIT 1")
    fun observeProjectCostSettings(projectId: String): Flow<ProjectCostSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProjectCostSettings(settings: ProjectCostSettingsEntity)

    @Query("SELECT * FROM interior_designs WHERE projectId=:projectId LIMIT 1")
    fun observeInteriorDesign(projectId: String): Flow<InteriorDesignEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveInteriorDesign(design: InteriorDesignEntity)

    @Query("SELECT * FROM dxf_import_records WHERE projectId=:projectId ORDER BY importedAt DESC")
    fun observeDxfImports(projectId: String): Flow<List<DxfImportRecordEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDxfImport(record: DxfImportRecordEntity)

    @Transaction
    suspend fun insertDxfImportTransaction(
        layers: List<CadLayerEntity>,
        entities: List<CadDrawingEntity>,
        record: DxfImportRecordEntity
    ) {
        if (layers.isNotEmpty()) insertCadLayers(layers)
        entities.forEach { insertCadEntity(it) }
        insertDxfImport(record)
    }

    @Query("SELECT * FROM cad_layers WHERE projectId=:projectId ORDER BY sortOrder")
    fun observeCadLayers(projectId: String): Flow<List<CadLayerEntity>>
    @Query("SELECT * FROM cad_entities WHERE projectId=:projectId ORDER BY updatedAt")
    fun observeCadEntities(projectId: String): Flow<List<CadDrawingEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCadLayer(layer: CadLayerEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCadLayers(layers: List<CadLayerEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCadEntity(entity: CadDrawingEntity)
    @Update
    suspend fun updateCadEntity(entity: CadDrawingEntity)
    @Update
    suspend fun updateCadLayer(layer: CadLayerEntity)
    @Query("DELETE FROM cad_entities WHERE id=:id")
    suspend fun deleteCadEntity(id: String)
}
