package com.majlisengineer.data
import androidx.room.Database
import androidx.room.RoomDatabase
@Database(entities=[MaterialCategoryEntity::class,MaterialEntity::class,MaterialImageEntity::class],version=1,exportSchema=true)
abstract class AppDatabase:RoomDatabase(){ abstract fun materialsDao():MaterialsDao }
