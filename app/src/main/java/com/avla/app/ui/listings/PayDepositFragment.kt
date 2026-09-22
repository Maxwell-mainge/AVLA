package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentPayDepositBinding
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * NEW — simulated M-Pesa STK push / reservation screen.
 *
 * No real payment gateway is called here. This demonstrates the escrow
 * reservation contract end-to-end (deposit held -> FirebaseRepository.
 * reserveUnit() -> pendingBalanceKsh on the landlord's doc -> settles after
 * confirm or the 48-hour window) at a scope appropriate for a diploma
 * project. See FirebaseRepository's RESERVATIONS & ESCROW section for the
 * real logic this screen triggers.
 */
class PayDepositFragment : Fragment() {

    private var _binding: FragmentPayDepositBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    private var listingId = ""
    private var depositKsh = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPayDepositBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listingId = arguments?.getString("listingId") ?: ""
        val listingTitle = arguments?.getString("listingTitle") ?: ""
        depositKsh = arguments?.getLong("depositKsh") ?: 0L

        binding.tvListingTitle.text = listingTitle
        binding.tvDepositAmount.text = "KSh ${"%,d".format(depositKsh)}"
        binding.btnPay.text = "Pay KSh ${"%,d".format(depositKsh)}"

        binding.btnPay.setOnClickListener { simulatePayment() }
        binding.btnDone.setOnClickListener { findNavController().popBackStack() }
    }

    private fun simulatePayment() {
        val pin = binding.etPin.text.toString().trim()
        if (pin.length != 4) {
            binding.tilPin.error = "Enter your 4-digit PIN"
            return
        }
        binding.tilPin.error = null

        if (listingId.isBlank()) {
            binding.root.showSnackbar("Listing information is missing")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            binding.root.showSnackbar("You need to be signed in to reserve a unit")
            return
        }

        binding.btnPay.isEnabled = false
        binding.tilPin.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.GONE
        binding.tvStkPrompt.text = "Waiting for M-Pesa confirmation..."

        lifecycleScope.launch {
            try {
                // Simulated STK push round-trip delay
                delay(2000)

                val studentProfile = repo.getUserProfile(currentUser.uid)
                val studentName = studentProfile?.fullName ?: currentUser.displayName.orEmpty()
                val studentPhone = studentProfile?.phone.orEmpty() // NEW — so the landlord can see/call who reserved

                repo.reserveUnit(listingId, currentUser.uid, studentName, studentPhone)

                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.tvStkPrompt.text = "Payment confirmed."
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.setTextColor(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.avla_green)
                )
                binding.tvStatus.text =
                    "Deposit held! You have 48 hours to confirm or reject after viewing the unit."
                binding.btnDone.visibility = View.VISIBLE

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.tvStkPrompt.text = "Enter your M-Pesa PIN to confirm this payment."
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                binding.tvStatus.text = "Reservation failed: ${e.message ?: "No units currently available"}"
                binding.btnPay.isEnabled = true
                binding.tilPin.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}