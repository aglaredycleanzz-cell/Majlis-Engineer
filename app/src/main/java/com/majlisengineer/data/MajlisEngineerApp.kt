package com.majlisengineer.data

import android.app.Application
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MajlisEngineerApp : Application() {
    lateinit var database: AppDatabase
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "majlis_engineer.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
            .build()
        scope.launch {
            val dao = database.materialsDao()
            try {
                dao.seedCatalogCategories(MaterialCategorySeed.categories)
                val catalog = applicationContext.assets.open(MaterialCatalogSeed.ASSET_NAME).use {
                    MaterialCatalogSeed.load(it)
                }
                if (dao.builtInCatalogCount() < catalog.size) {
                    dao.seedCatalogMaterials(catalog)
                    Log.i("MajlisEngineerApp", "Loaded ${catalog.size} built-in material catalog entries")
                }
            } catch (error: Exception) {
                Log.e("MajlisEngineerApp", "Unable to load the bundled material catalog", error)
            }
        }
    }
}
