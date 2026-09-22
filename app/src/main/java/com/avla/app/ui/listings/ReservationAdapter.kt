package com.avla.app.ui.listings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.R
import com.avla.app.data.model.Reservation
import com.avla.app.data.model.ReservationStatus
import com.avla.app.databinding.ItemReservationBinding

/**
 * Shows a list of reservations. Two modes:
 *  - Student mode (forLandlord = false, default): Confirm/Reject actions on
 *    anything still RESERVED, landlord's phone shown for contact, "View
 *    Full Listing" link since a sold-out listing leaves their browse feed.
 *  - Landlord mode (forLandlord = true): read-only — no Confirm/Reject,
 *    since the whole point of the escrow flow is that the STUDENT decides
 *    after physically viewing the unit. Shows the student's name + phone
 *    instead, so the landlord can actually coordinate a viewing.
 */
class ReservationAdapter(
    private val forLandlord: Boolean = false,
    private val onConfirm: (Reservation) -> Unit = {},
    private val onReject: (Reservation) -> Unit = {},
    private val onViewListing: (Reservation) -> Unit = {}
) : ListAdapter<Reservation, ReservationAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: ItemReservationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reservation: Reservation) {
            binding.tvListingTitle.text = reservation.listingTitle.ifBlank { "Listing" }
            binding.tvDeposit.text = "KSh ${"%,d".format(reservation.depositKsh)} deposit"

            val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
            binding.tvReservedAt.text = "Reserved ${fmt.format(java.util.Date(reservation.reservedAt))}"

            if (forLandlord) {
                // Landlord mode — who reserved it, read-only, their contact number.
                binding.tvSubtitle.visibility = View.VISIBLE
                binding.tvSubtitle.text = "Reserved by ${reservation.studentName.ifBlank { "a student" }}"

                binding.tvViewListing.visibility = View.GONE // it's their own listing, redundant
                binding.llActions.visibility = View.GONE // read-only — student decides, not landlord

                bindContactPhone(reservation.studentPhone)
            } else {
                // Student mode — landlord's contact, plus a way back to the
                // listing since it may have left the browse feed.
                binding.tvSubtitle.visibility = View.GONE
                binding.tvViewListing.visibility = View.VISIBLE
                binding.tvViewListing.setOnClickListener { onViewListing(reservation) }

                bindContactPhone(reservation.landlordPhone)
            }

            when (reservation.status) {
                ReservationStatus.RESERVED -> {
                    binding.tvStatus.text = "PENDING"
                    binding.tvStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.avla_orange)
                        )
                    binding.tvHint.visibility = View.VISIBLE
                    binding.tvHint.text = if (forLandlord) {
                        "Awaiting the student's decision — settles automatically after 48 hours if they don't respond."
                    } else {
                        "Visit the unit, then confirm or reject. If you don't respond within 48 hours, it settles automatically."
                    }
                    if (!forLandlord) {
                        binding.llActions.visibility = View.VISIBLE
                        binding.btnConfirm.setOnClickListener { onConfirm(reservation) }
                        binding.btnReject.setOnClickListener { onReject(reservation) }
                    }
                }
                ReservationStatus.CONFIRMED -> {
                    binding.tvStatus.text = "CONFIRMED"
                    binding.tvStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.avla_green)
                        )
                    binding.tvHint.visibility = View.VISIBLE
                    binding.tvHint.text = "This reservation is confirmed. The deposit has settled to the landlord."
                    binding.llActions.visibility = View.GONE
                }
                ReservationStatus.REJECTED -> {
                    binding.tvStatus.text = "REJECTED"
                    binding.tvStatus.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(
                            android.graphics.Color.parseColor("#D32F2F")
                        )
                    binding.tvHint.visibility = View.VISIBLE
                    binding.tvHint.text = "Your deposit was refunded." +
                            if (reservation.rejectionReason.isNotBlank()) " Reason: ${reservation.rejectionReason}" else ""
                    binding.llActions.visibility = View.GONE
                }
            }
        }

        private fun bindContactPhone(phone: String) {
            if (phone.isNotBlank()) {
                binding.llContactPhone.visibility = View.VISIBLE
                binding.tvContactPhone.text = phone
                binding.llContactPhone.setOnClickListener {
                    binding.root.context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:$phone")
                        )
                    )
                }
            } else {
                binding.llContactPhone.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReservationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<Reservation>() {
        override fun areItemsTheSame(old: Reservation, new: Reservation) = old.id == new.id
        override fun areContentsTheSame(old: Reservation, new: Reservation) = old == new
    }
}