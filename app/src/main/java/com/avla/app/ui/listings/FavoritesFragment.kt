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
import com.avla.app.databinding.FragmentFavoritesBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth

/**
 * Shows only the listings the student has favorited (heart-tapped).
 * Reuses ListingGridAdapter — same card layout as Home/Search.
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListingsViewModel by viewModels()
    private lateinit var adapter: ListingGridAdapter
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ListingGridAdapter(
            onItemClick = { listing ->
                val bundle = Bundle().apply {
                    putString("listingId", listing.id)
                }
                findNavController().navigate(R.id.listingDetailFragment, bundle)
            },
            onFavoriteClick = { listing ->
                // Un-favoriting here should also drop it from this list.
                viewModel.toggleFavorite(uid, listing.id)
            }
        )
        binding.rvListings.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvListings.adapter = adapter

        viewModel.favoriteListings.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is UiState.Loading
            when (state) {
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }

        viewModel.favoriteIds.observe(viewLifecycleOwner) { ids ->
            adapter.updateFavoriteIds(ids)
            // If the currently-displayed list still has an item whose ID was
            // just removed from favoriteIds, refresh so it drops off the screen.
            val currentList = adapter.currentList
            if (currentList.isNotEmpty() && currentList.any { it.id !in ids }) {
                viewModel.loadFavoriteListings(uid)
            }
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadFavoriteListings(uid) }
        viewModel.loadFavoriteListings(uid)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}