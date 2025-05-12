package com.allenchu66.geofenceapp.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.allenchu66.geofenceapp.model.ShareRequest
import com.allenchu66.geofenceapp.model.SharedUser
import com.allenchu66.geofenceapp.repository.SharedUserRepository

class SharedUserViewModel(private val repo: SharedUserRepository) : ViewModel() {
    // 已 accepted 的使用者 (地圖 & 好友清單)
    private val _sharedUsers = MutableLiveData<List<SharedUser>>()
    val sharedUsers: LiveData<List<SharedUser>> = _sharedUsers

    // 所有邀請請求 (pending / accepted / declined)
    private val  _shareRequests = MutableLiveData<List<ShareRequest>>()
    val shareRequests: LiveData<List<ShareRequest>> = _shareRequests

    // 發送/更新邀請結果
    private val _shareResult = MutableLiveData<Pair<Boolean, String>>()
    val shareResult: LiveData<Pair<Boolean, String>> = _shareResult

    init {
        loadSharedUsers()
    }

    /**
     * 監聽所有 ShareRequest，並回寫所有對方使用者資料與對應狀態
     */
    fun loadSharedUsers() {
        repo.listenToMyShareRequests { requests ->
            _shareRequests.postValue(requests)
            // 批次取得使用者資料，保留 status
            repo.fetchUsersForRequests(requests) { users ->
                _sharedUsers.postValue(users)
            }
        }
    }
    fun sendShareRequest(email: String) {
        repo.sendShareRequestByEmail(email) { success, msg ->
            _shareResult.postValue(success to msg)
        }
    }

    fun updateShareRequestStatus(email: String, status: String) {
        repo.updateShareRequestStatusByEmail(email, status) { success ->
            _shareResult.postValue(success to "")
        }
    }
}
