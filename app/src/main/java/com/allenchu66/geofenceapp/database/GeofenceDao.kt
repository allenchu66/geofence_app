package com.allenchu66.geofenceapp.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.allenchu66.geofenceapp.model.GeofenceEntity

@Dao
interface GeofenceDao {

    /**
     * Insert or update (upsert) a GeofenceEntity.
     * Replaces on conflict by primary key (fenceId).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeofenceEntity)

    /**
     * Get a single fence by its ID.
     */
    @Query("SELECT * FROM geofences WHERE fenceId = :id")
    suspend fun getById(id: String): GeofenceEntity?

    /**
     * Get all fences.
     */
    @Query("SELECT * FROM geofences")
    suspend fun getAll(): List<GeofenceEntity>

    /**
     * Delete a specific fence.
     */
    @Delete
    suspend fun delete(entity: GeofenceEntity)

    /**
     * Delete *all* fences.
     */
    @Query("DELETE FROM geofences")
    suspend fun deleteAll()
}
