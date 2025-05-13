package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.ShareRequest
import com.allenchu66.geofenceapp.model.SharedLocation
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LocationRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun uploadLocation(userId: String, lat: Double, lng: Double) {
        val locationData = hashMapOf(
            "user_id" to userId,
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("locations").document(userId)
            .set(locationData)
            .addOnSuccessListener { Log.d("LocationUpload", "Success") }
            .addOnFailureListener { e -> Log.e("LocationUpload", "Error", e) }
    }

    fun getSharedFriendUids(currentUserId: String, callback: (List<String>) -> Unit) {
        firestore.collection("users").document(currentUserId)
            .collection("shared_friends")
            .get()
            .addOnSuccessListener { snapshot ->
                val friendUids = snapshot.documents.mapNotNull { it.id }
                callback(friendUids)
            }
    }


    fun listenToSharedLocations(
        currentUserId: String,
        onUpdate: (List<SharedLocation>) -> Unit
    ) {
        val userMap = mutableMapOf<String, SharedUser>()
        val locationMap = mutableMapOf<String, LatLng>()
        Log.d("listenToSharedLocations","Test")
        firestore.collection("share_requests")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snap, err ->
                Log.d("listenToSharedLocations","Test1")
                if (err != null || snap == null) return@addSnapshotListener
                Log.d("listenToSharedLocations","Test2")
                val acceptedUids = snap.documents.mapNotNull { doc ->
                    doc.toObject(ShareRequest::class.java)
                        ?.takeIf { it.status == "accepted" }
                        ?.otherUid(currentUserId)
                }

                userMap.keys.retainAll(acceptedUids)
                locationMap.keys.retainAll(acceptedUids)

                Log.d("listenToSharedLocations",acceptedUids.size.toString())
                acceptedUids.forEach { uid ->
                    firestore.collection("users")
                        .document(uid)
                        .addSnapshotListener { userSnap, err ->
                            Log.d("Repo-UserSnap", "uid=$uid exists=${userSnap?.exists()} err=$err")
                            if (userSnap != null && userSnap.exists()) {
                                userSnap.toObject(SharedUser::class.java)?.let { user ->
                                    userMap[uid] = user
                                    emitSharedLocations(onUpdate, userMap, locationMap)
                                }
                            }
                        }
                    // 位置信息监听
                    firestore.collection("locations")
                        .document(uid)
                        .addSnapshotListener { locSnap, error ->
                            Log.d("Repo-LocSnap", "uid=$uid exists=${locSnap?.exists()} err=$error")
                            if (locSnap != null && locSnap.exists()) {
                                val lat = locSnap.getDouble("latitude") ?: return@addSnapshotListener
                                val lng = locSnap.getDouble("longitude") ?: return@addSnapshotListener
                                locationMap[uid] = LatLng(lat, lng)
                                Log.d("Repo-Location", "for $uid -> $lat,$lng")
                                emitSharedLocations(onUpdate, userMap, locationMap)
                            }
                        }
                }
            }
    }

    private fun emitSharedLocations(
        onUpdate: (List<SharedLocation>) -> Unit,
        userMap: Map<String, SharedUser>,
        locationMap: Map<String, LatLng>
    ) {
        val combined = userMap.mapNotNull { (uid, user) ->
            locationMap[uid]?.let { latLng ->
                SharedLocation(uid = uid, user = user, latLng = latLng)
            }
        }
        onUpdate(combined)
    }
}