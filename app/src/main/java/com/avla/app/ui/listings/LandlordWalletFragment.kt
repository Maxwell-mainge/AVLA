package com.avla.app.ui.listings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.avla.app.R
import com.avla.app.data.model.TransactionType
import com.avla.app.data.model.WalletTransaction
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentLandlordWalletBinding
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Landlord's wallet: pendingBalanceKsh vs availableBalanceKsh, a simulated
 * withdrawal action, and transaction history. See FirebaseRepository's
 * RESERVATIONS & ESCROW section for the underlying logic (withdrawFunds(),
 * fetchWalletTransactions()).
 *
 * NEW — type filter chips (All/Held/Settled/Refunded/Withdrawn). The full
 * history is fetched once and filtered client-side on chip taps, same
 * pattern as Admin Transactions and My Reservations.
 */
class LandlordWalletFragment : Fragment() {

    private var _binding: FragmentLandlordWalletBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private lateinit var adapter: WalletTransactionAdapter

    private var availableBalance = 0L
    private var allTransactions: List<WalletTransaction> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WalletTransactionAdapter()
        binding.rvTransactions.adapter = adapter
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        binding.btnWithdraw.setOnClickListener { showWithdrawDialog() }

        binding.tvViewReservations.setOnClickListener {
            findNavController().navigate(R.id.landlordReservationsFragment)
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val typeFilter = when (checkedIds.firstOrNull()) {
                binding.chipHeld.id -> TransactionType.DEPOSIT_HELD
                binding.chipSettled.id -> TransactionType.SETTLED
                binding.chipRefunded.id -> TransactionType.REFUNDED
                binding.chipWithdrawn.id -> TransactionType.WITHDRAWN
                else -> null // "All" chip, or nothing checked
            }
            applyTransactionFilter(typeFilter)
        }

        loadWallet()
    }

    private fun loadWallet() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val landlord = repo.getUserProfile(uid)
                allTransactions = repo.fetchWalletTransactions(uid)

                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE

                availableBalance = landlord?.availableBalanceKsh ?: 0L
                binding.tvPendingBalance.text = "KSh ${"%,d".format(landlord?.pendingBalanceKsh ?: 0L)}"
                binding.tvAvailableBalance.text = "KSh ${"%,d".format(availableBalance)}"
                binding.btnWithdraw.isEnabled = availableBalance > 0

                // Re-apply whichever chip is currently checked, so a
                // withdraw/reload doesn't silently reset the filter back to "All".
                val checkedId = binding.chipGroupFilter.checkedChipId
                val typeFilter = when (checkedId) {
                    binding.chipHeld.id -> TransactionType.DEPOSIT_HELD
                    binding.chipSettled.id -> TransactionType.SETTLED
                    binding.chipRefunded.id -> TransactionType.REFUNDED
                    binding.chipWithdrawn.id -> TransactionType.WITHDRAWN
                    else -> null
                }
                applyTransactionFilter(typeFilter)

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.root.showSnackbar("Failed to load wallet: ${e.message}")
            }
        }
    }

    private fun applyTransactionFilter(type: TransactionType?) {
        val filtered = if (type == null) allTransactions else allTransactions.filter { it.type == type }
        adapter.submitList(filtered)
        binding.tvEmptyHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showWithdrawDialog() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val amountInput = EditText(requireContext()).apply {
            hint = "Amount (max KSh ${"%,d".format(availableBalance)})"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Withdraw to M-Pesa")
            .setMessage("This simulates a B2C payout to your registered phone number.")
            .setView(amountInput)
            .setPositiveButton("Withdraw") { _, _ ->
                val amount = amountInput.text.toString().trim().toLongOrNull()
                if (amount == null || amount <= 0) {
                    binding.root.showSnackbar("Enter a valid amount")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        repo.withdrawFunds(uid, amount)
                        if (_binding != null) binding.root.showSnackbar("Withdrawal complete")
                        loadWallet()
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