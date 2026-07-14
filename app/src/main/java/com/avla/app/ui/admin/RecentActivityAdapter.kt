package com.avla.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.databinding.ItemRecentActivityBinding

/**
 * One row in the admin "Recent Activity" list.
 * iconTint should be one of your existing color resources, e.g. R.color.avla_orange.
 */
data class RecentActivityItem(
    @DrawableRes val iconRes: Int,
    @ColorRes val iconTint: Int,
    val title: String,
    val timeAgo: String
)

class RecentActivityAdapter(
    private var items: List<RecentActivityItem> = emptyList()
) : RecyclerView.Adapter<RecentActivityAdapter.ViewHolder>() {

    fun submitList(newItems: List<RecentActivityItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemRecentActivityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentActivityBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.ivActivityIcon.setImageResource(item.iconRes)
        holder.binding.ivActivityIcon.setColorFilter(
            holder.itemView.context.getColor(item.iconTint)
        )
        holder.binding.tvActivityTitle.text = item.title
        holder.binding.tvActivityTime.text = item.timeAgo

        // Hide the divider under the last item.
        holder.binding.dividerLine.visibility =
            if (position == items.size - 1) android.view.View.GONE else android.view.View.VISIBLE
    }

    override fun getItemCount(): Int = items.size
}