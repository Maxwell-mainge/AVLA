package com.avla.app.ui.listings

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.R
import com.avla.app.data.model.TransactionType
import com.avla.app.data.model.WalletTransaction
import com.avla.app.databinding.ItemWalletTransactionBinding

/** NEW — shows a landlord's wallet transaction history (deposits held, settlements, refunds, withdrawals, posting fees).
 *  Pass showLandlordName = true for the admin's platform-wide Transactions
 *  view, where knowing WHOSE balance changed matters; the landlord's own
 *  Wallet screen leaves it false since it would just repeat their own name.
 */
class WalletTransactionAdapter(
    private val showLandlordName: Boolean = false
) : ListAdapter<WalletTransaction, WalletTransactionAdapter.ViewHolder>(DiffCallback) {

    private val dateFmt = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())

    inner class ViewHolder(
        private val binding: ItemWalletTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(txn: WalletTransaction) {
            binding.tvDate.text = dateFmt.format(java.util.Date(txn.createdAt))

            val (label, color, sign) = when (txn.type) {
                TransactionType.DEPOSIT_HELD -> Triple("Deposit held", R.color.avla_orange, "")
                TransactionType.SETTLED      -> Triple("Reservation settled", R.color.avla_green, "+")
                TransactionType.REFUNDED     -> Triple("Deposit refunded", R.color.avla_orange, "-")
                TransactionType.WITHDRAWN    -> Triple("Withdrawal", R.color.avla_blue, "-")
                // NEW — landlord's simulated posting fee; money leaving the
                // landlord toward the platform, same "-" sign convention as
                // Withdrawn, kept visually distinct via its own label.
                TransactionType.PLATFORM_FEE -> Triple("Posting fee", R.color.avla_blue, "-")
            }

            binding.tvNote.text = txn.note.ifBlank { label }
            binding.tvAmount.text = "$sign KSh ${"%,d".format(txn.amountKsh)}"

            if (showLandlordName && txn.landlordName.isNotBlank()) {
                binding.tvLandlordName.visibility = android.view.View.VISIBLE
                binding.tvLandlordName.text = txn.landlordName
            } else {
                binding.tvLandlordName.visibility = android.view.View.GONE
            }

            val tint = ContextCompat.getColor(binding.root.context, color)
            binding.tvAmount.setTextColor(tint)
            binding.dotType.backgroundTintList = ColorStateList.valueOf(tint)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWalletTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<WalletTransaction>() {
        override fun areItemsTheSame(old: WalletTransaction, new: WalletTransaction) = old.id == new.id
        override fun areContentsTheSame(old: WalletTransaction, new: WalletTransaction) = old == new
    }
}