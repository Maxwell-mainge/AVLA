package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.avla.app.data.model.Listing
import com.avla.app.data.model.PropertyType
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentPostListingBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.convertDriveLinkToDirectUrl
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PostListingFragment : Fragment() {

    private var _binding: FragmentPostListingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListingsViewModel by viewModels()
    private val repo = FirebaseRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostListingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPost.setOnClickListener { validateAndPost() }

        viewModel.postState.observe(viewLifecycleOwner) { state ->
            binding.btnPost.isEnabled      = state !is UiState.Loading
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> {
                    binding.root.showSnackbar("Listing posted successfully!")
                    findNavController().popBackStack()
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    private fun validateAndPost() {
        val title       = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val location    = binding.etLocation.text.toString().trim()
        val nearCampus  = binding.etNearCampus.text.toString().trim()
        val priceStr    = binding.etPrice.text.toString().trim()
        val bedroomsStr = binding.etBedrooms.text.toString().trim()
        val amenities   = binding.etAmenities.text.toString()
            .split(",").map { it.trim() }.filter { it.isNotBlank() }

        // Collect up to 5 Google Drive image links, skip blanks
        val rawLinks = listOf(
            binding.etImageLink1.text.toString().trim(),
            binding.etImageLink2.text.toString().trim(),
            binding.etImageLink3.text.toString().trim(),
            binding.etImageLink4.text.toString().trim(),
            binding.etImageLink5.text.toString().trim()
        ).filter { it.isNotBlank() }

        // Convert all Drive share links to direct image URLs
        val imageUrls = rawLinks.map { convertDriveLinkToDirectUrl(it) }

        var valid = true
        if (title.isBlank())       { binding.tilTitle.error       = "Title is required";       valid = false }
        if (description.isBlank()) { binding.tilDescription.error = "Description is required"; valid = false }
        if (location.isBlank())    { binding.tilLocation.error    = "Location is required";    valid = false }
        if (priceStr.isBlank())    { binding.tilPrice.error       = "Price is required";       valid = false }
        if (!valid) return

        listOf(binding.tilTitle, binding.tilDescription, binding.tilLocation, binding.tilPrice)
            .forEach { it.error = null }

        val price = priceStr.toLongOrNull() ?: run {
            binding.tilPrice.error = "Enter a valid number"
            return
        }

        val bedrooms     = bedroomsStr.toIntOrNull() ?: 1
        val typeIndex    = binding.spinnerType.selectedItemPosition
        val propertyType = PropertyType.values().getOrElse(typeIndex) { PropertyType.BEDSITTER }
        val currentUser  = FirebaseAuth.getInstance().currentUser ?: return

        binding.btnPost.isEnabled      = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val landlordPhone = repo.getUserProfile(currentUser.uid)?.phone ?: ""

                val listing = Listing(
                    landlordUid   = currentUser.uid,
                    landlordName  = currentUser.displayName ?: "",
                    landlordPhone = landlordPhone,
                    title         = title,
                    description   = description,
                    propertyType  = propertyType,
                    priceKsh      = price,
                    bedrooms      = bedrooms,
                    location      = location,
                    nearCampus    = nearCampus,
                    amenities     = amenities,
                    imageUrls     = imageUrls
                )
                viewModel.postListing(listing)

            } catch (e: Exception) {
                binding.btnPost.isEnabled      = true
                binding.progressBar.visibility = View.GONE
                binding.root.showSnackbar("Failed: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}