package com.avla.app.ui.listings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.avla.app.R
import com.avla.app.data.model.Listing
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentListingDetailBinding
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ListingDetailFragment : Fragment() {

    private var _binding: FragmentListingDetailBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListingDetailBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    private var listingId = ""
    private var isAdmin = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Extract string data tracking identifier directly out of the standard platform Bundle
        listingId = arguments?.getString("listingId") ?: ""
        isAdmin = arguments?.getBoolean("isAdmin", false) ?: false
    }

    // NEW — reload every time this screen becomes visible again, not just
    // once. Without this, popping back here after paying a deposit (or
    // after the 48hr auto-settle changed something elsewhere) showed
    // whatever unitsAvailable/paused state was fetched before — stale.
    override fun onResume() {
        super.onResume()
        loadListing(listingId, isAdmin)
        loadInterestCount(listingId)
    }

    private fun loadListing(id: String, isAdmin: Boolean) {
        if (id.isBlank()) {
            if (_binding != null) binding.root.showSnackbar("Listing ID is missing")
            return
        }

        FirebaseFirestore
            .getInstance()
            .collection("listings")
            .document(id)
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener // view destroyed before this returned

                val listing = snapshot.toObject(Listing::class.java)

                if (listing == null) {
                    binding.root.showSnackbar("Listing not found")
                    return@addOnSuccessListener
                }

                showListing(listing, isAdmin)
            }
            .addOnFailureListener {
                if (_binding != null) binding.root.showSnackbar("Failed loading listing")
            }
    }

    // NEW — overrides the Reserve & Pay button into a distinct "already
    // reserved by you" state, so it can't look identical to a fresh,
    // payable listing right after a student just paid a deposit on it.
    private fun checkOwnActiveReservation(listingId: String) {
        val studentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val existing = repo.fetchActiveReservation(listingId, studentUid)
                if (_binding == null || existing == null) return@launch

                binding.btnReserve.isEnabled = true
                binding.btnReserve.text = "Reservation Pending — Tap to View"
                binding.btnReserve.setOnClickListener {
                    findNavController().navigate(R.id.myReservationsFragment)
                }
            } catch (_: Exception) {
                // Silently skip — if this check fails, the button just stays
                // in its normal payable state rather than blocking the screen.
            }
        }
    }

    private fun loadInterestCount(id: String) {
        if (id.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = repo.getFavoriteCount(id)
                if (_binding == null) return@launch

                if (count > 0) {
                    binding.tvInterestCount.text =
                        if (count == 1) "1 student interested" else "$count students interested"
                    binding.tvInterestCount.visibility = View.VISIBLE
                } else {
                    binding.tvInterestCount.visibility = View.GONE
                }
            } catch (_: Exception) {
                // Silently skip — this is a nice-to-have signal, not critical path
                binding.tvInterestCount.visibility = View.GONE
            }
        }
    }

    private fun setupDotIndicator(pageCount: Int, viewPager: ViewPager2) {
        if (_binding == null || pageCount <= 1) {
            binding.llDotsIndicator.visibility = View.GONE
            return
        }
        binding.llDotsIndicator.visibility = View.VISIBLE
        binding.llDotsIndicator.removeAllViews()

        val dotSize = (8 * resources.displayMetrics.density).toInt()
        val dotMargin = (3 * resources.displayMetrics.density).toInt()

        val dots = (0 until pageCount).map { index ->
            ImageView(requireContext()).apply {
                val params = android.widget.LinearLayout.LayoutParams(dotSize, dotSize).also {
                    it.marginStart = dotMargin
                    it.marginEnd = dotMargin
                }
                layoutParams = params
                setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (index == 0) R.drawable.dot_active else R.drawable.dot_inactive
                    )
                )
                binding.llDotsIndicator.addView(this)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dots.forEachIndexed { index, dot ->
                    dot.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            if (index == position) R.drawable.dot_active else R.drawable.dot_inactive
                        )
                    )
                }
            }
        })
    }

    private fun showListing(listing: Listing, isAdmin: Boolean) {
        binding.tvTitle.text = listing.title
        binding.tvLocation.text = listing.location
        binding.tvNearCampus.text = "Near ${listing.nearCampus}"
        binding.tvPrice.text = "KSh ${listing.priceKsh}/month"
        binding.tvType.text = listing.propertyType.displayName()
        binding.tvBedrooms.text = "${listing.bedrooms} bedroom"
        binding.tvDescription.text = listing.description
        binding.tvAmenities.text = listing.amenities.joinToString(" • ")

        // NEW — multi-unit availability status, same "hide for single unit" rule as the card
        if (listing.totalUnits > 1) {
            binding.tvUnitsAvailable.visibility = View.VISIBLE
            binding.tvUnitsAvailable.text =
                "${listing.unitsAvailable} of ${listing.totalUnits} units available"
        } else {
            binding.tvUnitsAvailable.visibility = View.GONE
        }

        // NEW — Reserve & Pay entry point; students only, reflects
        // sold-out/paused state from the multi-unit escrow design.
        if (isAdmin) {
            binding.btnReserve.visibility = View.GONE
        } else {
            binding.btnReserve.visibility = View.VISIBLE
            when {
                listing.paused -> {
                    binding.btnReserve.isEnabled = false
                    binding.btnReserve.text = "Not Available Right Now"
                }
                listing.unitsAvailable <= 0 -> {
                    binding.btnReserve.isEnabled = false
                    binding.btnReserve.text = "Fully Booked"
                }
                else -> {
                    binding.btnReserve.isEnabled = true
                    binding.btnReserve.text = "Reserve • Pay KSh ${"%,d".format(listing.depositKsh)} Deposit"
                    binding.btnReserve.setOnClickListener {
                        val bundle = Bundle().apply {
                            putString("listingId", listing.id)
                            putString("listingTitle", listing.title)
                            putLong("depositKsh", listing.depositKsh)
                        }
                        findNavController().navigate(R.id.payDepositFragment, bundle)
                    }
                }
            }
            // NEW — checked regardless of which branch above ran: a listing
            // can show "Fully Booked" for everyone else while still being
            // THIS student's own pending reservation (e.g. a single-unit
            // listing they just reserved, revisited via My Reservations'
            // "View Full Listing" link). When that's the case, override
            // whatever text/state was just set — this always wins.
            checkOwnActiveReservation(listing.id)
        }

        val adapter = ImageSliderAdapter(listing.imageUrls)
        binding.vpImages.adapter = adapter

        setupDotIndicator(listing.imageUrls.size, binding.vpImages)

        binding.btnCall.setOnClickListener {
            val phone = listing.landlordPhone
            startActivity(
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:$phone")
                )
            )
        }

        binding.btnWhatsapp.setOnClickListener {
            // Convert local format (07XXXXXXXX) to international (2547XXXXXXXX)
            val number = listing.landlordPhone.replaceFirst("0", "254")
            val url = "https://wa.me/$number"
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        }

        if (isAdmin) {
            binding.btnAdminDelete.visibility = View.VISIBLE
            binding.btnAdminDelete.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Permanently delete?")
                    .setMessage("\"${listing.title}\" will be permanently removed.")
                    .setPositiveButton("Delete") { _, _ ->
                        FirebaseFirestore.getInstance()
                            .collection("listings").document(listing.id)
                            .delete()
                            .addOnSuccessListener {
                                if (_binding != null) {
                                    binding.root.showSnackbar("Listing deleted")
                                }
                                findNavController().popBackStack()
                            }
                            .addOnFailureListener {
                                if (_binding != null) {
                                    binding.root.showSnackbar("Failed to delete listing")
                                }
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}