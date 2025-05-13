package com.allenchu66.geofenceapp.model

data class SharedUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUri: String = "",
    val status: String = "",     // "pending" / "accepted" / "declined"
    val inviter: String = "",     // 哪個 UID 發出邀請
    val fcmToken: String = ""
)
