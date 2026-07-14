package com.avla.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.avla.app.R
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.model.PropertyType
import com.avla.app.databinding.FragmentAdminListingsBinding
import com.avla.app.ui.listings.ListingGridAdapter
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar

class AdminListingsFragment : Fragment() {

    private var _binding: FragmentAdminListingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()

    // Maps the text shown in the landlord autocomplete ("Jane Doe (0722...)")
    // back to the landlord's uid, since names alone aren't guaranteed unique.
    private val landlordNameToUid = mutableMapOf<String, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminListingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tapping a listing now opens the full detail view (photos, price,
        // description, interest count) instead of jumping straight to delete.
        // isLandlordView = true reuses the read-only heart mode, which stays
        // fully hidden here since we never populate favorite counts for admin —
        // admin doesn't need a favorite indicator at all on this screen.
        val adapter = ListingGridAdapter(
            isLandlordView = true,
            showPostedDate = true,
            onItemClick = { listing ->
                val bundle = Bundle().apply {
                    putString("listingId", listing.id)
                    putBoolean("isAdmin", true)
                }
                findNavController().navigate(
                    R.id.action_adminListingsFragment_to_listingDetailFragment,
                    bundle
                )
            }
        )
        binding.rvListings.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvListings.adapter = adapter

        viewModel.allListings.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is UiState.Error   -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }

        viewModel.actionState.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.root.showSnackbar(state.data)
        }

        viewModel.landlords.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) setupLandlordAutocomplete(state.data)
        }

        binding.btnSearch.setOnClickListener { applyFilters() }
        binding.btnClearFilters.setOnClickListener { clearFilters() }
        binding.btnToggleFilters.setOnClickListener { toggleFilterPanel() }

        viewModel.loadAllListings()
        viewModel.loadLandlords()
    }

    /**
     * Filters start collapsed so the listing grid gets most of the screen by
     * default — this panel used to eat so much vertical space that only ~4
     * listings were visible before scrolling.
     */
    private fun toggleFilterPanel() {
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, AutoTransition().apply { duration = 200 })
        val expanding = binding.filterPanel.visibility != View.VISIBLE
        binding.filterPanel.visibility = if (expanding) View.VISIBLE else View.GONE
        binding.btnToggleFilters.text = if (expanding) "Hide Filters" else "Filters"
    }

    private fun setupLandlordAutocomplete(landlords: List<AppUser>) {
        landlordNameToUid.clear()
        val displayNames = landlords.map { landlord ->
            val label = if (landlord.phone.isNotBlank()) {
                "${landlord.fullName} (${landlord.phone})"
            } else {
                landlord.fullName
            }
            landlordNameToUid[label] = landlord.uid
            label
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, displayNames)
        binding.etLandlord.setAdapter(adapter)
    }

    private fun applyFilters() {
        val landlordText = binding.etLandlord.text?.toString()?.trim().orEmpty()
        val landlordUid = landlordNameToUid[landlordText] // null if blank or free-typed text that doesn't match a suggestion

        val minPrice = binding.etMinPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val maxPrice = binding.etMaxPrice.text?.toString()?.trim()?.toLongOrNull() ?: Long.MAX_VALUE
        val location = binding.etLocation.text?.toString()?.trim().orEmpty()

        val typePosition = binding.spinnerPropertyType.selectedItemPosition
        val propertyType = if (typePosition <= 0) null else PropertyType.values()[typePosition - 1]

        val filter = ListingFilter(
            minPriceKsh = minPrice,
            maxPriceKsh = maxPrice,
            propertyType = propertyType,
            location = location,
            landlordUid = landlordUid
        )

        viewModel.applyListingFilter(filter)
    }

    private fun clearFilters() {
        binding.etLandlord.setText("")
        binding.etMinPrice.setText("")
        binding.etMaxPrice.setText("")
        binding.etLocation.setText("")
        binding.spinnerPropertyType.setSelection(0)
        viewModel.loadAllListings()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}