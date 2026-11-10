package com.majlisengineer.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MaterialCategoryEntity::class, MaterialEntity::class, MaterialImageEntity::class,
        ProjectEntity::class, SiteSurveyEntity::class, BoqItemEntity::class, ProjectTaskEntity::class,
        DrawingNoteEntity::class, CadLayerEntity::class, CadDrawingEntity::class],
    version = 4,
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
    }
}
