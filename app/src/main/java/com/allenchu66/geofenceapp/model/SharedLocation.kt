package com.allenchu66.geofenceapp.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp

data class SharedLocation(
    val user: SharedUser,
    val uid: String,
    val latLng: LatLng,
    val timestamp: Timestamp? = null
)