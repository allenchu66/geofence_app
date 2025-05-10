package com.allenchu66.geofenceapp.model

data class SharedUser(
    val email: String = "",
    val displayName: String = "",
    val photoUri: String = "",
    val status: String = ""  // "waiting", "shared", "none"
)
