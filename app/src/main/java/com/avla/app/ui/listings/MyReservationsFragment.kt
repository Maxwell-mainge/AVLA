package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.model.Reservation
import com.avla.app.data.model.ReservationStatus
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentMyReservationsBinding
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Student's list of reservations, with Confirm/Reject actions on the escrow
 * flow started from PayDepositFragment. See FirebaseRepository's
 * confirmReservation()/rejectReservation() for the underlying logic.
 *
 * NEW — status filter chips (All / Pending / Confirmed / Rejected). The
 * full list is fetched once per load and filtered client-side on chip taps,
 * same pattern as Admin Transactions.
 */
class MyReservationsFragment : Fragment() {

    private var _binding: FragmentMyReservationsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private lateinit var adapter: ReservationAdapter

    private var allReservations: List<Reservation> = emptyList()
    private var currentFilter: ReservationStatus? = null // null = "All"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyReservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReservationAdapter(
            onConfirm = { reservation -> confirmReservation(reservation) },
            onReject = { reservation -> showRejectDialog(reservation) },
            onViewListing = { reservation ->
                val bundle = Bundle().apply {
                    putString("listingId", reservation.listingId)
                }
                findNavController().navigate(R.id.listingDetailFragment, bundle)
            }
        )
        binding.rvReservations.adapter = adapter
        binding.rvReservations.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                binding.chipPending.id -> ReservationStatus.RESERVED
                binding.chipConfirmed.id -> ReservationStatus.CONFIRMED
                binding.chipRejected.id -> ReservationStatus.REJECTED
                else -> null // "All" chip, or nothing checked
            }
            applyFilter()
        }

        loadReservations()
    }

    private fun loadReservations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                allReservations = repo.fetchReservationsForStudent(uid)
                if (_binding == null) return@launch

                binding.progressBar.visibility = View.GONE
                applyFilter() // keeps whatever chip was already selected

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.root.showSnackbar("Failed to load reservations: ${e.message}")
            }
        }
    }

    private fun applyFilter() {
        val filtered = currentFilter?.let { status -> allReservations.filter { it.status == status } }
            ?: allReservations
        adapter.submitList(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmReservation(reservation: Reservation) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm this unit?")
            .setMessage("This releases your KSh ${"%,d".format(reservation.depositKsh)} deposit to the landlord. Only confirm after you've viewed the unit and are happy with it.")
            .setPositiveButton("Confirm") { _, _ ->
                lifecycleScope.launch {
                    try {
                        repo.confirmReservation(reservation.id)
                        if (_binding != null) binding.root.showSnackbar("Reservation confirmed")
                        loadReservations()
                    } catch (e: Exception) {
                        if (_binding != null) binding.root.showSnackbar("Failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRejectDialog(reservation: Reservation) {
        val reasonInput = EditText(requireContext()).apply {
            hint = "Why are you rejecting this unit? (optional)"
            setPadding(48, 24, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject this unit?")
            .setMessage("Your KSh ${"%,d".format(reservation.depositKsh)} deposit will be refunded and the unit becomes available again.")
            .setView(reasonInput)
            .setPositiveButton("Reject") { _, _ ->
                val reason = reasonInput.text.toString().trim()
                lifecycleScope.launch {
                    try {
                        repo.rejectReservation(reservation.id, reason)
                        if (_binding != null) binding.root.showSnackbar("Reservation rejected — deposit refunded")
                        loadReservations()
                    } catch (e: Exception) {
                        if (_binding != null) binding.root.showSnackbar("Failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}