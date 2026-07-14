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

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Extract string data tracking identifier directly out of the standard platform Bundle
        val listingId = arguments?.getString("listingId") ?: ""
        val isAdmin = arguments?.getBoolean("isAdmin", false) ?: false
        loadListing(listingId, isAdmin)
        loadInterestCount(listingId)
    }

    private fun loadListing(id: String, isAdmin: Boolean) {
        if (id.isBlank()) {
            binding.root.showSnackbar("Listing ID is missing")
            return
        }

        FirebaseFirestore
            .getInstance()
            .collection("listings")
            .document(id)
            .get()
            .addOnSuccessListener { snapshot ->
                val listing = snapshot.toObject(Listing::class.java)

                if (listing == null) {
                    binding.root.showSnackbar("Listing not found")
                    return@addOnSuccessListener
                }

                showListing(listing, isAdmin)
            }
            .addOnFailureListener {
                binding.root.showSnackbar("Failed loading listing")
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