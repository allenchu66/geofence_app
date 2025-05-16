package com.allenchu66.geofenceapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.allenchu66.geofenceapp.Converters
import com.allenchu66.geofenceapp.model.GeofenceEntity

@Database(entities = [GeofenceEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class GeofenceDatabase : RoomDatabase() {
    abstract fun geofenceDao(): GeofenceDao
    companion object {
        @Volatile private var INSTANCE: GeofenceDatabase? = null

        fun getInstance(context: Context): GeofenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GeofenceDatabase::class.java,
                    "app_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}