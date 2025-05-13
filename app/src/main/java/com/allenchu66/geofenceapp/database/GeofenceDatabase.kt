package com.allenchu66.geofenceapp.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.allenchu66.geofenceapp.Converters
import com.allenchu66.geofenceapp.model.GeofenceEntity

@Database(entities = [GeofenceEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class GeofenceDatabase : RoomDatabase() {
    abstract fun geofenceDao(): GeofenceDao
}