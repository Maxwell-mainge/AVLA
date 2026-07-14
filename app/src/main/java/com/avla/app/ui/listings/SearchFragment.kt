package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.avla.app.R
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.model.PropertyType
import com.avla.app.databinding.FragmentSearchBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar

/**
 * Search/Filter screen.
 */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListingsViewModel by viewModels()
    private lateinit var adapter: ListingGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fixed navigation parameter mapping utilizing explicit destination target layout indices
        adapter = ListingGridAdapter(
            onItemClick = { listing ->
                val bundle = Bundle().apply {
                    putString("listingId", listing.id)
                }
                findNavController().navigate(R.id.listingDetailFragment, bundle)
            }
        )
        binding.rvResults.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvResults.adapter = adapter

        binding.btnSearch.setOnClickListener { applyFilter() }

        viewModel.listings.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Loading -> Unit
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility =
                        if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
            }
        }
    }

    private fun applyFilter() {
        val minPrice = binding.etMinPrice.text.toString().toLongOrNull() ?: 0L
        val maxPrice = binding.etMaxPrice.text.toString().toLongOrNull() ?: 50_000L
        val location = binding.etLocation.text.toString().trim()

        val typeIndex = binding.spinnerPropertyType.selectedItemPosition
        val propertyType = if (typeIndex == 0) null
        else PropertyType.values().getOrNull(typeIndex - 1)

        val filter = ListingFilter(
            minPriceKsh = minPrice,
            maxPriceKsh = maxPrice,
            propertyType = propertyType,
            location = location
        )
        viewModel.searchListings(filter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
