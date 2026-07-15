package com.avla.app.ui.listings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.R
import com.avla.app.data.model.Listing
import com.avla.app.databinding.ItemListingGridBinding
import com.avla.app.utils.convertDriveLinkToDirectUrl
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions

class ListingGridAdapter(
    private val onItemClick: (Listing) -> Unit,
    private val onItemLongClick: ((Listing) -> Unit)? = null,
    private val onFavoriteClick: ((Listing) -> Unit)? = null,
    private var favoriteIds: Set<String> = emptySet(),
    /**
     * When true, the heart is a read-only "interest" indicator for landlords
     * instead of a clickable toggle: filled red with a count when someone is
     * interested, hidden entirely when no one is.
     */

    private val isLandlordView: Boolean = false,
    private var favoriteCounts: Map<String, Int> = emptyMap(),
    /**
     * When true, shows a "Posted [date]" line on each card — used only in
     * admin's "All Listings" screen so students/landlords don't see clutter
     * they don't need on their own browsing/management grids.
     */
    private val showPostedDate: Boolean = false
) : ListAdapter<Listing, ListingGridAdapter.ViewHolder>(DiffCallback) {

    /**
     * Call this whenever the set of favorited listing IDs changes
     * (e.g. from a favoriteIds LiveData observer) to refresh heart icons.
     * Used in student (interactive) mode.
     */
    fun updateFavoriteIds(newFavoriteIds: Set<String>) {
        favoriteIds = newFavoriteIds
        notifyDataSetChanged()
    }

    /**
     * Call this after fetching per-listing favorite counts to refresh the
     * read-only interest display. Used in landlord (isLandlordView) mode.
     */
    fun updateFavoriteCounts(newCounts: Map<String, Int>) {
        favoriteCounts = newCounts
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemListingGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(listing: Listing) {
            binding.tvTitle.text    = listing.title
            binding.tvLocation.text = listing.location
            binding.tvPrice.text    = "KSh ${"%,d".format(listing.priceKsh)} /mo"

            if (showPostedDate) {
                val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                binding.tvPostedDate.text = "Posted ${fmt.format(java.util.Date(listing.createdAt))}"
                binding.tvPostedDate.visibility = View.VISIBLE
            } else {
                binding.tvPostedDate.visibility = View.GONE
            }

            // "NEW" badge — computed from createdAt, no separate flag needed.
            val sevenDaysMillis = 7 * 24 * 60 * 60 * 1000L
            val isNew = (System.currentTimeMillis() - listing.createdAt) <= sevenDaysMillis
            binding.tvNewBadge.visibility = if (isNew) View.VISIBLE else View.GONE

            loadImage(listing.imageUrls.firstOrNull())

            binding.root.setOnClickListener { onItemClick(listing) }

            binding.root.setOnLongClickListener {
                onItemLongClick?.invoke(listing)
                true
            }

            if (isLandlordView) {
                bindLandlordInterestDisplay(listing)
            } else {
                bindStudentFavoriteToggle(listing)
            }
        }

        private fun bindStudentFavoriteToggle(listing: Listing) {
            binding.tvFavoriteCount.visibility = View.GONE

            val isFavorited = favoriteIds.contains(listing.id)
            binding.ivFavorite.visibility = View.VISIBLE
            binding.ivFavorite.setImageResource(
                if (isFavorited) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            binding.ivFavorite.setColorFilter(
                if (isFavorited)
                    android.graphics.Color.parseColor("#E53935")
                else
                    androidx.core.content.ContextCompat.getColor(
                        binding.root.context, R.color.avla_green
                    )
            )
            binding.ivFavorite.setOnClickListener {
                onFavoriteClick?.invoke(listing)
            }
        }

        private fun bindLandlordInterestDisplay(listing: Listing) {
            // Not clickable for landlords — this is a read-only interest signal.
            binding.ivFavorite.setOnClickListener(null)

            val count = favoriteCounts[listing.id] ?: 0
            if (count > 0) {
                binding.ivFavorite.visibility = View.VISIBLE
                binding.ivFavorite.setImageResource(R.drawable.ic_favorite)
                binding.ivFavorite.setColorFilter(android.graphics.Color.parseColor("#E53935"))
                binding.tvFavoriteCount.visibility = View.VISIBLE
                binding.tvFavoriteCount.text = count.toString()
            } else {
                // "Remain empty if no one is interested"
                binding.ivFavorite.visibility = View.GONE
                binding.tvFavoriteCount.visibility = View.GONE
            }
        }

        private fun loadImage(rawUrl: String?) {
            if (rawUrl.isNullOrBlank()) {
                binding.ivThumbnail.setImageResource(R.drawable.ic_placeholder_house)
                return
            }

            val loadUrl = convertDriveLinkToDirectUrl(rawUrl)

            Glide.with(binding.ivThumbnail.context)
                .load(loadUrl)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.ic_placeholder_house)
                        .error(R.drawable.ic_placeholder_house)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .timeout(15_000)
                )
                .into(binding.ivThumbnail)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListingGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<Listing>() {
        override fun areItemsTheSame(old: Listing, new: Listing) = old.id == new.id
        override fun areContentsTheSame(old: Listing, new: Listing) = old == new
    }
}