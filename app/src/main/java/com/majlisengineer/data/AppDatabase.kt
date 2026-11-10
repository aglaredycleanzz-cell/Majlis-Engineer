package com.majlisengineer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MaterialCategoryEntity::class, MaterialEntity::class, MaterialImageEntity::class,
        ProjectEntity::class, SiteSurveyEntity::class, BoqItemEntity::class, ProjectTaskEntity::class,
        DrawingNoteEntity::class, CadLayerEntity::class, CadDrawingEntity::class,
        SupplierEntity::class, MaterialSupplierOfferEntity::class, ProjectMaterialEntity::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun materialsDao(): MaterialsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS projects (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, clientName TEXT NOT NULL,
                    clientPhone TEXT NOT NULL, location TEXT NOT NULL, projectType TEXT NOT NULL,
                    status TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_createdAt ON projects(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_status ON projects(status)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS site_surveys (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, spaceName TEXT NOT NULL,
                    lengthM REAL NOT NULL, widthM REAL NOT NULL, heightM REAL NOT NULL,
                    openingsAreaM2 REAL NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_site_surveys_projectId ON site_surveys(projectId)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS boq_items (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, description TEXT NOT NULL,
                    unit TEXT NOT NULL, quantity REAL NOT NULL, unitRate REAL NOT NULL,
                    category TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_boq_items_projectId ON boq_items(projectId)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS project_tasks (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, title TEXT NOT NULL,
                    details TEXT NOT NULL, status TEXT NOT NULL, dueAt INTEGER, createdAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_tasks_projectId ON project_tasks(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_tasks_status ON project_tasks(status)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS drawing_notes (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, layer TEXT NOT NULL,
                    title TEXT NOT NULL, description TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_drawing_notes_projectId ON drawing_notes(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_drawing_notes_layer ON drawing_notes(layer)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS cad_layers (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, name TEXT NOT NULL,
                    colorHex TEXT NOT NULL, visible INTEGER NOT NULL, sortOrder INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cad_layers_projectId ON cad_layers(projectId)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS cad_entities (
                    id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, layerId TEXT NOT NULL,
                    type TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, width REAL NOT NULL,
                    height REAL NOT NULL, label TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cad_entities_projectId ON cad_entities(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cad_entities_layerId ON cad_entities(layerId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE boq_items ADD COLUMN wastePercent REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE boq_items ADD COLUMN purchaseIncrement REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE boq_items ADD COLUMN materialUnitRate REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE boq_items ADD COLUMN laborUnitRate REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE projects ADD COLUMN startDate TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE projects ADD COLUMN expectedDeliveryDate TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE materials ADD COLUMN baseUnit TEXT NOT NULL DEFAULT 'قطعة'")
                database.execSQL("ALTER TABLE materials ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE materials ADD COLUMN baseUnitPriceOmr REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE materials ADD COLUMN basePriceUpdatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE TABLE IF NOT EXISTS suppliers (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, contactName TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, location TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_name ON suppliers(name)")
                database.execSQL("CREATE TABLE IF NOT EXISTS material_supplier_offers (id TEXT NOT NULL PRIMARY KEY, materialId TEXT NOT NULL, supplierId TEXT NOT NULL, brand TEXT NOT NULL, unit TEXT NOT NULL, unitPriceOmr REAL NOT NULL, notes TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_material_supplier_offers_materialId ON material_supplier_offers(materialId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_material_supplier_offers_supplierId ON material_supplier_offers(supplierId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_material_supplier_offers_materialId_supplierId_updatedAt ON material_supplier_offers(materialId, supplierId, updatedAt)")
                database.execSQL("CREATE TABLE IF NOT EXISTS project_materials (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, materialId TEXT NOT NULL, unitSnapshot TEXT NOT NULL, requiredQuantity REAL NOT NULL, usedQuantity REAL NOT NULL, projectUnitPriceOverrideOmr REAL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_materials_projectId ON project_materials(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_materials_materialId ON project_materials(materialId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_materials_projectId_materialId ON project_materials(projectId, materialId)")
            }
        }
    }
}
