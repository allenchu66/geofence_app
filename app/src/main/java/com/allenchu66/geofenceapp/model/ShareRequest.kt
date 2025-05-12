package com.allenchu66.geofenceapp.model

import com.google.firebase.Timestamp

data class ShareRequest(
    val inviter: String = "",
    val participants: List<String> = emptyList(),
    val status: String = "",
    val invitedAt: Timestamp? = null,
    val acceptedAt: Timestamp? = null
){
    fun otherUid(myUid: String) = participants.first { it != myUid }
}