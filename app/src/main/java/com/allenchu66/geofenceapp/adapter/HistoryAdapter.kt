package com.allenchu66.geofenceapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.allenchu66.geofenceapp.databinding.ItemHistoryBinding
import com.allenchu66.geofenceapp.model.LocationCluster
import com.github.vipulasri.timelineview.TimelineView
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private var items: List<LocationCluster>,
    private val onClick: (LocationCluster) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(
        val binding: ItemHistoryBinding,
        viewType: Int
    ) : RecyclerView.ViewHolder(binding.root){
        init {
            binding.timeline.initLine(viewType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding,viewType)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val cluster = items[position]
        with(holder.binding) {
            val start = formatHourMinute(cluster.startTs)
            val end   = formatHourMinute(cluster.endTs)
            tvTimeRange.text = "${start} – ${end}"
            tvLocation.text =  "地點 : ${cluster.locationName}"

            root.setOnClickListener {
                onClick(cluster)
            }
        }
    }

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun formatHourMinute(ts: com.google.firebase.Timestamp): String {
        return timeFormatter.format(ts.toDate())
    }

    override fun getItemViewType(position: Int): Int {
        return TimelineView.getTimeLineViewType(position, itemCount)
    }

    override fun getItemCount() = items.size

    fun updateList(cluster: List<LocationCluster>) {
        items = cluster
        notifyDataSetChanged()
    }
}
