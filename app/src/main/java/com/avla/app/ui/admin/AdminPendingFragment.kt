package com.avla.app.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.data.model.AppUser
import com.avla.app.databinding.FragmentAdminPendingBinding
import com.avla.app.databinding.ItemPendingLandlordBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AdminPendingFragment : Fragment() {

    private var _binding: FragmentAdminPendingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPendingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PendingAdapter(
            onApprove = { user -> confirmApprove(user) },
            onReject  = { user -> showRejectDialog(user) },
            onViewNationalId = { user -> openLink(user.nationalIdDocLink) },
            onViewPropertyDoc = { user -> openLink(user.propertyDocLink) }
        )

        binding.rvPending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPending.adapter = adapter

        viewModel.pendingLandlords.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility =
                if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> {
                    adapter.submitList(state.data)
                    binding.tvEmpty.visibility =
                        if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }

        viewModel.actionState.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.root.showSnackbar(state.data)
            if (state is UiState.Error)   binding.root.showSnackbar(state.message)
        }

        viewModel.loadPendingLandlords()
    }

    private fun openLink(link: String) {
        if (link.isBlank() || link == "mock") {
            binding.root.showSnackbar("No document link available")
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    }

    private fun confirmApprove(user: AppUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Approve ${user.fullName}?")
            .setMessage("This will grant them full access to post listings.")
            .setPositiveButton("Approve") { _, _ -> viewModel.approveLandlord(user.uid) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRejectDialog(user: AppUser) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Reason for rejection"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject ${user.fullName}?")
            .setMessage("The landlord will be notified and can resubmit documents.")
            .setView(input)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString().trim()
                    .ifBlank { "Documents not sufficient. Please resubmit." }
                viewModel.rejectLandlord(user.uid, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    inner class PendingAdapter(
        private val onApprove: (AppUser) -> Unit,
        private val onReject: (AppUser) -> Unit,
        private val onViewNationalId: (AppUser) -> Unit,
        private val onViewPropertyDoc: (AppUser) -> Unit
    ) : RecyclerView.Adapter<PendingAdapter.VH>() {

        private val items = mutableListOf<AppUser>()

        fun submitList(list: List<AppUser>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(val b: ItemPendingLandlordBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPendingLandlordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = items[position]
            val b    = holder.b

            b.tvName.text     = user.fullName
            b.tvEmail.text    = user.email
            b.tvPhone.text    = "📞 ${user.phone}"
            b.tvLocation.text = "📍 ${user.operatingLocation}"
            b.tvDocType.text  = "Property Doc: ${user.propertyDocType}"

            // Show/hide doc buttons based on whether links exist
            b.btnViewNationalId.isEnabled  = user.nationalIdDocLink.isNotBlank() && user.nationalIdDocLink != "mock"
            b.btnViewPropertyDoc.isEnabled = user.propertyDocLink.isNotBlank()   && user.propertyDocLink != "mock"

            b.btnViewNationalId.setOnClickListener  { onViewNationalId(user) }
            b.btnViewPropertyDoc.setOnClickListener { onViewPropertyDoc(user) }
            b.btnApprove.setOnClickListener         { onApprove(user) }
            b.btnReject.setOnClickListener          { onReject(user) }
        }
    }
}