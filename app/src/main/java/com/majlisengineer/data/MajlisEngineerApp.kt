package com.majlisengineer.data
import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
class MajlisEngineerApp:Application(){
 lateinit var database:AppDatabase
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 override fun onCreate(){
  super.onCreate()
  database=Room.databaseBuilder(applicationContext,AppDatabase::class.java,"majlis_engineer.db").build()
  scope.launch { val dao=database.materialsDao(); if(dao.categoryCount()==0) dao.insertCategories(MaterialCategorySeed.categories) }
 }
}
