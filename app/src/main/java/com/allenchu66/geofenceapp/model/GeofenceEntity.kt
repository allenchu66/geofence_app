package com.allenchu66.geofenceapp.model

import androidx.room.*
import java.util.*

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val fenceId: String,
    val ownerUid: String,
    val targetUid: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val locationName: String,
    val transition: List<String>,
    val createdAt: Long,   // millis
    val updatedAt: Long    // millis
)
