package com.avla.app.ui.listings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
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

        // NOTE: postState is no longer driven by this screen's submit flow —
        // posting now goes through showPostingFeeDialog()'s own coroutine
        // (see below), since it needs to sequence the fee payment before the
        // listing is actually created. Left in place in case postState is
        // still used elsewhere / for future wiring; harmless either way.
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
        val totalUnitsStr = binding.etTotalUnits.text.toString().trim()
        val depositStr  = binding.etDeposit.text.toString().trim()
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
        if (depositStr.isBlank())  { binding.tilDeposit.error     = "Deposit amount is required"; valid = false }
        if (!valid) return

        listOf(binding.tilTitle, binding.tilDescription, binding.tilLocation, binding.tilPrice, binding.tilDeposit)
            .forEach { it.error = null }

        val price = priceStr.toLongOrNull() ?: run {
            binding.tilPrice.error = "Enter a valid number"
            return
        }
        val deposit = depositStr.toLongOrNull() ?: run {
            binding.tilDeposit.error = "Enter a valid number"
            return
        }

        val totalUnits = totalUnitsStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val typeIndex    = binding.spinnerType.selectedItemPosition
        val propertyType = PropertyType.values().getOrElse(typeIndex) { PropertyType.BEDSITTER }
        val bedrooms     = bedroomsFor(propertyType) // NEW — was a separate, redundant manual field
        val currentUser  = FirebaseAuth.getInstance().currentUser ?: return

        val listing = Listing(
            landlordUid   = currentUser.uid,
            landlordName  = currentUser.displayName ?: "",
            landlordPhone = "", // filled in inside the dialog's coroutine, see below
            title         = title,
            description   = description,
            propertyType  = propertyType,
            priceKsh      = price,
            bedrooms      = bedrooms,
            location      = location,
            nearCampus    = nearCampus,
            amenities     = amenities,
            imageUrls     = imageUrls,
            totalUnits     = totalUnits,
            unitsAvailable = totalUnits, // starts full; decremented by reserveUnit()
            depositKsh     = deposit
        )

        // NEW — landlords now pay a posting fee too, same simulated-M-Pesa
        // pattern already used for the student deposit, so both sides of
        // the marketplace pay something.
        showPostingFeeDialog(listing, currentUser.uid)
    }

    /**
     * NEW — a flat, simulated posting fee a landlord pays before their
     * listing actually goes live. Mirrors PayDepositFragment's simulated
     * STK push, just inline as a dialog rather than its own screen since
     * this is a much smaller flow. Logged as a PLATFORM_FEE transaction —
     * see FirebaseRepository.payListingPostingFee() — separate from and
     * never touching the landlord's escrow balances.
     */
    private fun showPostingFeeDialog(listing: Listing, landlordUid: String) {
        val feeAmount = 500L
        val pinInput = EditText(requireContext()).apply {
            hint = "M-Pesa PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pay Listing Fee")
            .setMessage("A KSh $feeAmount posting fee applies to publish this listing (simulated M-Pesa STK push).")
            .setView(pinInput)
            .setPositiveButton("Pay KSh $feeAmount") { _, _ ->
                val pin = pinInput.text.toString().trim()
                if (pin.length != 4) {
                    binding.root.showSnackbar("Enter your 4-digit PIN")
                    return@setPositiveButton
                }

                binding.btnPost.isEnabled      = false
                binding.progressBar.visibility = View.VISIBLE

                lifecycleScope.launch {
                    try {
                        delay(1500) // simulated STK push round-trip

                        val landlordPhone = repo.getUserProfile(landlordUid)?.phone ?: ""
                        val listingId = repo.postListing(listing.copy(landlordPhone = landlordPhone))
                        repo.payListingPostingFee(landlordUid, listingId, feeAmount)

                        if (_binding == null) return@launch
                        binding.root.showSnackbar("Listing posted — KSh $feeAmount fee paid")
                        findNavController().popBackStack()

                    } catch (e: Exception) {
                        if (_binding == null) return@launch
                        binding.btnPost.isEnabled      = true
                        binding.progressBar.visibility = View.GONE
                        binding.root.showSnackbar("Failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // NEW — the property type dropdown already implies a bedroom count, so a
    // separate manual "Number of Bedrooms" field was redundant and could
    // disagree with the selected type (e.g. "Studio" + typed "2"). Derived
    // here instead.
    private fun bedroomsFor(type: PropertyType): Int = when (type) {
        PropertyType.BEDSITTER, PropertyType.STUDIO, PropertyType.ONE_BEDROOM -> 1
        PropertyType.TWO_BEDROOM -> 2
        PropertyType.THREE_BEDROOM -> 3
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}