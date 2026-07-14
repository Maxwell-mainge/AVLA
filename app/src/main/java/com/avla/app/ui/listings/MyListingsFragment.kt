package com.avla.app.ui.listings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentMyListingsBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MyListingsFragment : Fragment() {

    private var _binding: FragmentMyListingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ListingsViewModel by viewModels()
    private val repo = FirebaseRepository()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyListingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadWelcomeHeader()

        val adapter = ListingGridAdapter(
            isLandlordView = true, // read-only interest display, not a favorite toggle
            onItemClick = { listing ->
                if (_binding != null) {
                    val bundle = Bundle().apply {
                        putString("listingId", listing.id)
                    }

                    findNavController().navigate(
                        com.avla.app.R.id.action_myListingsFragment_to_landlordManageListingFragment,
                        bundle
                    )
                }
            },
            onItemLongClick = { listing ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete listing?")
                    .setMessage("\"${listing.title}\" will be permanently deleted.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteListing(listing.id, uid)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvMyListings.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvMyListings.adapter = adapter

        viewModel.myListings.observe(viewLifecycleOwner) { state ->
            if (_binding == null) return@observe

            binding.progressBar.visibility =
                if (state is UiState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is UiState.Loading -> Unit
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility =
                        if (state.data.isEmpty()) View.VISIBLE else View.GONE

                    val word = if (state.data.size == 1) "listing" else "listings"
                    binding.tvListingCount.text = "${state.data.size} $word posted"

                    // Fetch interest counts for the listings now showing
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val counts = repo.getFavoriteCounts(state.data.map { it.id })
                            if (_binding != null) adapter.updateFavoriteCounts(counts)
                        } catch (_: Exception) {
                            // Non-critical — leave hearts hidden on failure
                        }
                    }
                }
                is UiState.Error -> {
                    binding.root.showSnackbar(state.message)
                }
            }
        }

        viewModel.loadMyListings(uid)
    }

    private fun loadWelcomeHeader() {
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
                if (_binding != null) {
                    binding.tvWelcome.text = "Welcome 👋"
                    binding.tvAvatarInitial.text = "?"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}