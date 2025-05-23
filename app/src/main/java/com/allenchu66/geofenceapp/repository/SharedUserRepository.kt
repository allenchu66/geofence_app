package com.allenchu66.geofenceapp.repository

import android.util.Log
import com.allenchu66.geofenceapp.model.ShareRequest
import com.allenchu66.geofenceapp.model.SharedUser
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

class SharedUserRepository {
    private val firestore = Firebase.firestore
    val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "SharedUserRepository"
    }

    private fun pairId(uid1: String, uid2: String) =
        listOf(uid1, uid2)
            .sorted()
            .joinToString("_")

    /** 監聽所有跟我有關的 share_requests */
    fun listenToMyShareRequests(onResult: (List<ShareRequest>) -> Unit) {
        val me = auth.currentUser?.uid ?: return
        firestore.collection("share_requests")
            .whereArrayContains("participants", me)
            .addSnapshotListener { snaps, err ->
                if (err != null || snaps == null) {
                    onResult(emptyList()); return@addSnapshotListener
                }
                val list = snaps.documents.mapNotNull { it.toObject(ShareRequest::class.java) }
                onResult(list)
            }
    }

    fun fetchUsersForRequests(
        requests: List<ShareRequest>,
        onResult: (List<SharedUser>) -> Unit
    ) {
        val meUid = auth.currentUser?.uid ?: run {
            onResult(emptyList()); return
        }
        if (requests.isEmpty()) {
            onResult(emptyList()); return
        }
        // 為每個請求建立一個取 user 的 Task 並記錄對應狀態
        val tasks = requests.map { req ->
            val otherUid = req.otherUid(meUid)
            firestore.collection("users").document(otherUid).get()
                .continueWith { task ->
                    val doc = task.result!!
                    SharedUser(
                        uid = doc.id,
                        email = doc.getString("email").orEmpty(),
                        displayName = doc.getString("displayName").orEmpty(),
                        photoUri = doc.getString("photoUri").orEmpty(),
                        status = req.status,       // pending/accepted/declined
                        inviter    = req.inviter
                    )
                }
        }
        @Suppress("UNCHECKED_CAST")
        Tasks.whenAllSuccess<SharedUser>(tasks)
            .addOnSuccessListener { users ->
                onResult(users)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    fun updateShareRequestStatusByEmail(
        email: String,
        status: String,  // "pending" / "accepted" / "declined"
        onFinish: (Boolean) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return
        val currentUid = currentUser.uid

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onFinish(false)
                    return@addOnSuccessListener
                }

                val targetUid = snapshot.documents.first().id
                val pairId = pairId(currentUid, targetUid)

                val reqRef = db.collection("share_requests").document(pairId)
                val updates = mutableMapOf<String, Any>(
                    "status" to status
                )
                if (status == "accepted") {
                    updates["acceptedAt"] = FieldValue.serverTimestamp()
                } else if (status == "pending") {
                    updates["invitedAt"] = FieldValue.serverTimestamp()
                }

                reqRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener { onFinish(true) }
                    .addOnFailureListener {e ->
                        Log.e(TAG, "Failed to update share_request ($pairId) to status=$status", e)
                        onFinish(false)
                    }
            }
            .addOnFailureListener {e ->
                Log.e(TAG, "Error querying users collection for email=$email", e)
                onFinish(false) }
    }


    fun sendShareRequestByEmail(
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(false, "您尚未登入 請先登入！")
            return
        }
        val currentUid = currentUser.uid
        if (currentUser.email == email) {
            onResult(false, "您不能跟自己分享")
            return
        }

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { query ->
                if (query.isEmpty) {
                    onResult(false, "No user found with this email.")
                    return@addOnSuccessListener
                }
                val targetUid = query.documents.first().id

                val pid = pairId(currentUid, targetUid)

                val requestData = mapOf(
                    "inviter" to currentUid,
                    "participants" to listOf(currentUid, targetUid),
                    "status" to "pending",
                    "invitedAt" to FieldValue.serverTimestamp()
                )

                db.collection("share_requests")
                    .document(pid)
                    .set(requestData, SetOptions.merge())
                    .addOnSuccessListener {
                        onResult(true, "已發送邀請")
                    }
                    .addOnFailureListener { e ->
                        onResult(false, e.message ?: "邀請時發生錯誤")
                        Log.e(TAG, "sendShareRequestByEmail=$email", e)
                    }
            }
            .addOnFailureListener { e ->
                onResult(false, e.message ?: "找不到使用者")
                Log.e(TAG, "sendShareRequestByEmail=$email", e)
            }
    }

}
