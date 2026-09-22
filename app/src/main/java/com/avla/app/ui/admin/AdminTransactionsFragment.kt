package com.avla.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.avla.app.data.model.TransactionType
import com.avla.app.data.model.WalletTransaction
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentAdminTransactionsBinding
import com.avla.app.ui.listings.WalletTransactionAdapter
import com.avla.app.utils.showSnackbar
import kotlinx.coroutines.launch

/**
 * NEW — admin's platform-wide view of every wallet transaction across all
 * landlords. Replaces the old bottom-nav "Pending" shortcut (redundant with
 * Overview's Pending card) so admin has visibility into what's actually
 * moving through the escrow system, not just verification requests.
 *
 * NEW — filter chips (All / Held / Settled / Refunded / Withdrawn / Posting
 * Fees). The full list is fetched once via fetchAllWalletTransactions() and
 * filtered client-side on chip taps, since re-querying Firestore per filter
 * tap would be wasteful for what's just a type filter over already-loaded
 * data.
 */
class AdminTransactionsFragment : Fragment() {

    private var _binding: FragmentAdminTransactionsBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()
    private lateinit var adapter: WalletTransactionAdapter

    private var allTransactions: List<WalletTransaction> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // showLandlordName = true — this is the one place it matters which
        // landlord each entry belongs to.
        adapter = WalletTransactionAdapter(showLandlordName = true)
        binding.rvTransactions.adapter = adapter
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val typeFilter = when (checkedIds.firstOrNull()) {
                binding.chipHeld.id -> TransactionType.DEPOSIT_HELD
                binding.chipSettled.id -> TransactionType.SETTLED
                binding.chipRefunded.id -> TransactionType.REFUNDED
                binding.chipWithdrawn.id -> TransactionType.WITHDRAWN
                binding.chipPostingFee.id -> TransactionType.PLATFORM_FEE // NEW
                else -> null // "All" chip, or nothing checked
            }
            applyFilter(typeFilter)
        }

        loadTransactions()
    }

    private fun loadTransactions() {
        binding.progressBar.visibility = View.VISIBLE
        binding.llEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                allTransactions = repo.fetchAllWalletTransactions()
                if (_binding == null) return@launch

                binding.progressBar.visibility = View.GONE
                applyFilter(null) // "All" — matches the default-checked chip

            } catch (e: Exception) {
                if (_binding == null) return@launch
                binding.progressBar.visibility = View.GONE
                binding.root.showSnackbar("Failed to load transactions: ${e.message}")
            }
        }
    }

    private fun applyFilter(type: TransactionType?) {
        val filtered = if (type == null) allTransactions else allTransactions.filter { it.type == type }
        adapter.submitList(filtered)
        binding.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}