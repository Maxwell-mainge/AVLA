package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentLandlordReservationsBinding
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * NEW — landlord's read-only view of who has reserved units on their
 * listings. Fills a real gap: FirebaseRepository.fetchReservationsForLandlord()
 * already existed (it drives the 48hr lazy-settlement check), but until now
 * nothing in the UI actually surfaced it — a landlord had no way to see who
 * reserved a unit or how to reach them to coordinate a viewing.
 *
 * Deliberately read-only: no Confirm/Reject here. The whole point of the
 * escrow design is that the STUDENT decides after physically viewing the
 * unit — see MyReservationsFragment for that side.
 */
class LandlordReservationsFragment : Fragment() {

    private var _binding: FragmentLandlordReservationsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordReservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ReservationAdapter(forLandlord = true)
        binding.rvReservations.adapter = adapter
        binding.rvReservations.layoutManager = LinearLayoutManager(requireContext())

        loadReservations()
    }

    // NEW — reload every time this screen becomes visible, same reasoning
    // as Home/Listing Detail: a landlord glancing back at this screen after
    // handling a reservation elsewhere should see current state, not
    // whatever was loaded the first time.
    override fun onResume() {
        super.onResume()
        if (_binding != null) loadReservations()
    }

    private fun loadReservations() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val reservations = repo.fetchReservationsForLandlord(uid)
                if (_binding == null) return@launch

                binding.progressBar.visibility = View.GONE
                (binding.rvReservations.adapter as ReservationAdapter).submitList(reservations)
                binding.llEmptyState.visibility = if (reservations.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.root.showSnackbar("Failed to load reservations: ${e.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}