package com.avla.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.avla.app.R
import com.avla.app.data.model.ActivityLog
import com.avla.app.databinding.FragmentAdminOverviewBinding
import com.avla.app.utils.UiState

/**
 * Admin landing screen — quick stats and shortcuts into each admin section,
 * instead of dropping straight into Pending Verifications.
 */
class AdminOverviewFragment : Fragment() {

    private var _binding: FragmentAdminOverviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()
    private lateinit var activityAdapter: RecentActivityAdapter

    // Tracks the 4 in-flight loads triggered by a pull-to-refresh, so we
    // only stop the spinner once everything has come back.
    private var pendingRefreshCalls = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardPending.setOnClickListener {
            findNavController().navigate(R.id.adminPendingFragment)
        }
        binding.cardListings.setOnClickListener {
            findNavController().navigate(R.id.adminListingsFragment)
        }
        binding.cardUsers.setOnClickListener {
            findNavController().navigate(R.id.adminUsersFragment)
        }

        binding.btnReviewPending.setOnClickListener {
            findNavController().navigate(R.id.adminPendingFragment)
        }
        binding.btnViewListings.setOnClickListener {
            findNavController().navigate(R.id.adminListingsFragment)
        }
        binding.btnViewUsers.setOnClickListener {
            findNavController().navigate(R.id.adminUsersFragment)
        }

        binding.swipeRefresh.setOnRefreshListener { loadAllData() }

        setupRecentActivity()

        viewModel.pendingLandlords.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.tvPendingCount.text = state.data.size.toString()
            onLoadFinished(state)
        }
        viewModel.allListings.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.tvListingsCount.text = state.data.size.toString()
            onLoadFinished(state)
        }
        viewModel.allUsers.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.tvUsersCount.text = state.data.size.toString()
            onLoadFinished(state)
        }
        viewModel.recentActivity.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) {
                if (state.data.isEmpty()) {
                    showEmptyActivityState()
                } else {
                    activityAdapter.submitList(state.data.map { toRecentActivityItem(it) })
                    binding.rvRecentActivity.visibility = View.VISIBLE
                    binding.tvActivityEmptyState.visibility = View.GONE
                }
            } else if (state is UiState.Error) {
                showEmptyActivityState()
            }
            onLoadFinished(state)
        }

        loadAllData()
    }

    private fun loadAllData() {
        pendingRefreshCalls = 4
        viewModel.loadPendingLandlords()
        viewModel.loadAllListings()
        viewModel.loadAllUsers()
        viewModel.loadRecentActivity()
    }

    /**
     * Called from every LiveData observer above. Once all 4 loads have
     * resolved (success or error), stops the pull-to-refresh spinner.
     * Ignores the initial UiState.Loading emission on first load.
     */
    private fun onLoadFinished(state: UiState<*>) {
        if (state is UiState.Loading) return
        pendingRefreshCalls = (pendingRefreshCalls - 1).coerceAtLeast(0)
        if (pendingRefreshCalls == 0) {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupRecentActivity() {
        activityAdapter = RecentActivityAdapter()
        binding.rvRecentActivity.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentActivity.adapter = activityAdapter
        showEmptyActivityState()
    }

    private fun showEmptyActivityState() {
        binding.rvRecentActivity.visibility = View.GONE
        binding.tvActivityEmptyState.visibility = View.VISIBLE
    }

    private fun toRecentActivityItem(log: ActivityLog): RecentActivityItem {
        val (iconRes, colorRes) = when (log.type) {
            "landlord_registered" -> R.drawable.ic_people to R.color.avla_orange
            "listing_published"   -> R.drawable.ic_listings to R.color.avla_green
            "landlord_verified"   -> R.drawable.ic_pending to R.color.avla_green
            "user_created"        -> R.drawable.ic_student_cap to R.color.avla_blue
            else                  -> R.drawable.ic_people to R.color.avla_green
        }
        return RecentActivityItem(
            iconRes = iconRes,
            iconTint = colorRes,
            title = log.title,
            timeAgo = formatTimeAgo(log.timestamp)
        )
    }

    private fun formatTimeAgo(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return ""
        val diffMinutes = (System.currentTimeMillis() - timestamp.toDate().time) / 60000
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}