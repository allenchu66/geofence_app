package com.allenchu66.geofenceapp.adapter

import android.util.Log
import com.allenchu66.geofenceapp.databinding.SharedUserItemBinding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.model.SharedUser
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class SharedUserAdapter(
    private var users: List<SharedUser>,
    private val onActionClick: (SharedUser) -> Unit
) : RecyclerView.Adapter<SharedUserAdapter.SharedUserViewHolder>() {

    inner class SharedUserViewHolder(val binding: SharedUserItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedUserViewHolder {
        val binding = SharedUserItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SharedUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SharedUserViewHolder, position: Int) {
        val user = users[position]
        val b = holder.binding
        val meUid = FirebaseAuth.getInstance().currentUser?.uid

        val photoUri = user.photoUri
        if (photoUri != null) {
            Glide.with(b.imgAvatar.context)
                .load(photoUri)
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(b.imgAvatar)
        } else {
            b.imgAvatar.setImageResource(R.drawable.ic_default_avatar)
        }

        b.textNickname.text = user.displayName
        b.textStatus.text = when (user.status) {
            "pending" -> {
                if (user.inviter == meUid) {
                    // 我是邀請者，還在等對方同意
                    "等待對方確認"
                } else {
                    // 對方邀請我
                    "邀請你共享位置"
                }
            }
            "accepted" -> "已共享位置"
            "declined"  -> {
                if (user.inviter == meUid) {
                    // 我發出邀請，但被對方拒絕
                    "對方已拒絕"
                } else {
                    // 我拒絕了這個邀請
                    "你已拒絕"
                }
            }
            else -> ""
        }

        b.btnAction.setOnClickListener {
            onActionClick(user)
        }
    }

    override fun getItemCount(): Int = users.size

    fun updateList(newUsers: List<SharedUser>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
