package com.allenchu66.geofenceapp.repository

import com.allenchu66.geofenceapp.database.GeofenceDao
import com.allenchu66.geofenceapp.model.GeofenceData
import com.allenchu66.geofenceapp.model.GeofenceEntity

class GeofenceLocalRepository(private val dao: GeofenceDao) {
    suspend fun save(entry: GeofenceData) {
        dao.upsert(
            GeofenceEntity(
            fenceId   = entry.fenceId,
            ownerUid  = entry.ownerUid,
            targetUid = entry.targetUid,
            latitude  = entry.latitude,
            longitude = entry.longitude,
            radius    = entry.radius,
            locationName = entry.locationName,
            transition= entry.transition,
            createdAt = entry.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        )
    }

    suspend fun get(fenceId: String) = dao.getById(fenceId)
    suspend fun getAll() = dao.getAll()
    suspend fun delete(fenceId: String) {
        dao.getById(fenceId)?.let { dao.delete(it) }
    }
    suspend fun deleteAll() = dao.deleteAll()
}
