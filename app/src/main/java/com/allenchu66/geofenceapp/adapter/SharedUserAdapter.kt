package com.allenchu66.geofenceapp.adapter

import android.util.Log
import com.allenchu66.geofenceapp.databinding.SharedUserItemBinding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.allenchu66.geofenceapp.R
import com.allenchu66.geofenceapp.model.SharedUser
import com.bumptech.glide.Glide

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

        b.textEmail.text = user.email
        b.textStatus.text = when (user.status) {
            "pending" -> "邀請你共享位置"
            "waiting" -> "等待對方確認"
            "shared" -> "已共享位置"
            "declined" -> "對方已拒絕"
            "cancelled" -> "對方取消邀請"
            "none" -> "尚未共享"
            else -> user.status
        }

        // 根據狀態設定按鈕文字
        b.btnAction.text = when (user.status) {
            "pending" -> "接受"
            "waiting" -> "取消邀請"
            "shared" -> "停止共享"
            "declined" -> "重新邀請"
            "cancelled" -> "重新邀請"
            "none" -> "邀請"
            else -> "操作"
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
