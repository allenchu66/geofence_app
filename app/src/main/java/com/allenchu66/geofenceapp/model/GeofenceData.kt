package com.allenchu66.geofenceapp.model

data class GeofenceData(
    val fenceId: String = "",
    val ownerUid: String = "",
    val targetUid: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 0f,
    val locationName: String = "",
    val transition: List<String> = listOf("enter", "exit"),
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null
)