package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.avla.app.R
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentHomeBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Student home — 2-column grid sorted by proximity to student's registered campus.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListingsViewModel by viewModels()
    private val repo = FirebaseRepository()
    private lateinit var adapter: ListingGridAdapter
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private var studentCampus = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        studentCampus = arguments?.getString("campus") ?: ""

        loadWelcomeName()

        // Navigates directly using explicit Resource ID and a system bundle argument
        adapter = ListingGridAdapter(
            onItemClick = { listing ->
                val bundle = Bundle().apply {
                    putString("listingId", listing.id)
                }
                findNavController().navigate(R.id.listingDetailFragment, bundle)
            },
            onFavoriteClick = { listing ->
                viewModel.toggleFavorite(uid, listing.id)
            }
        )
        binding.rvListings.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvListings.adapter = adapter

        viewModel.listings.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = state is UiState.Loading
            when (state) {
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvListingCount.text = buildCountText(state.data, studentCampus)
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }

        viewModel.favoriteIds.observe(viewLifecycleOwner) { ids ->
            adapter.updateFavoriteIds(ids)
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadListingsByProximity(studentCampus) }
    }

    // NEW — was only loaded once in onViewCreated, so a listing's
    // unitsAvailable/paused state changing (e.g. right after a student pays
    // a deposit on it) didn't show up on Home until the app was fully
    // restarted. onResume fires every time this screen becomes visible
    // again, so the feed now reflects reality whenever the student returns
    // to it — including the very first time, since onResume always follows
    // onViewCreated.
    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        viewModel.loadListingsByProximity(studentCampus)
        viewModel.loadFavoriteIds(uid)
    }

    private fun buildCountText(listings: List<com.avla.app.data.model.Listing>, campus: String): String {
        val total = listings.size

        if (campus.isBlank()) {
            val word = if (total == 1) "listing" else "listings"
            return "$total $word available"
        }

        // The list itself intentionally shows ALL available listings (with
        // campus matches sorted to the top) — so we count actual matches
        // separately rather than claiming the whole total is "near campus".
        val nearCount = listings.count { it.nearCampus.equals(campus, ignoreCase = true) }

        return when {
            nearCount == 0 -> {
                val word = if (total == 1) "listing" else "listings"
                "No listings near $campus yet — $total $word available elsewhere"
            }
            nearCount == total -> {
                val word = if (nearCount == 1) "listing" else "listings"
                "$nearCount $word near $campus"
            }
            else -> {
                val nearWord = if (nearCount == 1) "listing" else "listings"
                "$nearCount $nearWord near $campus · $total total"
            }
        }
    }

    private fun loadWelcomeName() {
        val currentUid = uid
        if (currentUid.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = repo.getUserProfile(currentUid)
                if (_binding == null) return@launch
                val firstName = user?.fullName?.trim()?.split(" ")?.firstOrNull()
                binding.tvWelcome.text = if (!firstName.isNullOrBlank()) "Hi, $firstName 👋" else "Welcome 👋"
                binding.tvAvatarInitial.text = firstName?.take(1)?.uppercase() ?: "?"
            } catch (_: Exception) {
                if (_binding != null) binding.tvWelcome.text = "Welcome 👋"
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}