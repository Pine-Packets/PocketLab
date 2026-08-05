package com.pineandpackets.pocketlab.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CaseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PocketLabDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    
    companion object {
        @Volatile
        private var INSTANCE: PocketLabDatabase? = null
        
        fun getDatabase(context: Context): PocketLabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketLabDatabase::class.java,
                    "pocketlab_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
