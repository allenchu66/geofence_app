package com.allenchu66.geofenceapp.model

import com.google.android.gms.maps.model.LatLng

data class SharedLocation(
    val user: SharedUser,
    val uid: String,
    val latLng: LatLng
)