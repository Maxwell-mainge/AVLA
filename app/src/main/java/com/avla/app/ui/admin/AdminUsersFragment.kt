package com.avla.app.ui.admin

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.avla.app.databinding.FragmentAdminUsersBinding
import com.avla.app.databinding.ItemUserBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AdminUsersFragment : Fragment() {

    private var _binding: FragmentAdminUsersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()
    private lateinit var adapter: UserAdapter
    private var allUsers: List<AppUser> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UserAdapter { user -> showUserOptions(user) }
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter

        binding.chipGroup.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter() }
            override fun afterTextChanged(s: Editable?) {}
        })

        viewModel.allUsers.observe(viewLifecycleOwner) { state ->
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> { allUsers = state.data; applyFilter() }
                is UiState.Error   -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }

        viewModel.actionState.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) binding.root.showSnackbar(state.data)
            if (state is UiState.Error)   binding.root.showSnackbar(state.message)
        }

        viewModel.loadAllUsers()
    }

    private fun applyFilter() {
        val query = binding.etSearch.text.toString().trim().lowercase()
        val filtered = allUsers
            // Admin accounts are management infrastructure, not something
            // another admin needs to browse/moderate here — always excluded.
            .filter { it.role != UserRole.ADMIN }
            .filter { user ->
                when (binding.chipGroup.checkedChipId) {
                    binding.chipStudents.id  -> user.role == UserRole.STUDENT
                    binding.chipLandlords.id -> user.role == UserRole.LANDLORD
                    binding.chipVerified.id  -> user.role == UserRole.LANDLORD && user.verificationStatus == VerificationStatus.VERIFIED
                    binding.chipPending.id   -> user.role == UserRole.LANDLORD && user.verificationStatus == VerificationStatus.PENDING
                    else -> true
                }
            }
            .filter { query.isBlank() || it.fullName.lowercase().contains(query) || it.email.lowercase().contains(query) }

        adapter.submitList(filtered)
        binding.tvCount.text = "${filtered.size} user${if (filtered.size != 1) "s" else ""}"
    }

    private fun showUserOptions(user: AppUser) {
        // Don't allow suspending admin accounts
        if (user.role == UserRole.ADMIN) return

        when (user.role) {
            UserRole.LANDLORD -> showLandlordOptions(user)
            UserRole.STUDENT -> showStudentOptions(user)
            else -> Unit
        }
    }

    /**
     * Landlords keep their documents on file permanently, not just during
     * initial verification — admin can pull them up any time (e.g. if a
     * dispute comes up later), not only while status is still PENDING.
     */
    /**
     * Replaces setItems(...) with custom rows — default Material list dialogs
     * render as bare text with no divider or tap feedback, which looked like
     * floating, disconnected labels.
     */
    private fun showOptionsDialog(title: String, options: List<String>, onSelect: (String) -> Unit) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        options.forEachIndexed { index, option ->
            val row = android.widget.TextView(requireContext()).apply {
                text = option
                textSize = 15f
                setTextColor(
                    if (option == "Suspend")
                        android.graphics.Color.parseColor("#C62828")
                    else android.graphics.Color.parseColor("#212121")
                )
                setPadding(dp(24), dp(14), dp(24), dp(14))
                setBackgroundResource(typedValue.resourceId)
                isClickable = true
                isFocusable = true
            }
            container.addView(row)
            if (index != options.lastIndex) {
                container.addView(android.view.View(requireContext()).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                })
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(container)
            .create()

        options.forEach { option ->
            (container.getChildAt(options.indexOf(option) * 2) as android.widget.TextView)
                .setOnClickListener {
                    dialog.dismiss()
                    onSelect(option)
                }
        }

        dialog.show()
    }

    private fun showLandlordOptions(user: AppUser) {
        val options = mutableListOf("View Documents")
        if (user.verificationStatus == VerificationStatus.VERIFIED) {
            options.add("Flag Documents")
        }
        options.add(if (user.suspended) "Unsuspend" else "Suspend")

        showOptionsDialog(user.fullName, options) { selected ->
            when (selected) {
                "View Documents" -> showLandlordDocuments(user)
                "Flag Documents" -> promptFlagLandlordDocuments(user)
                "Suspend" -> promptSuspend(user)
                "Unsuspend" -> promptUnsuspend(user)
            }
        }
    }

    /**
     * For a landlord already VERIFIED, but a problem with their documents
     * surfaces later on spot-check. Reuses the same REJECTED status and
     * resubmit flow already built for the initial review stage — sets them
     * back to REJECTED, which LoginFragment already routes to the pending/
     * resubmit screen, blocking the app until they fix it and get
     * re-approved. This is a harder block than the student ID flag, which
     * is appropriate given landlords are actively posting listings.
     */
    private fun promptFlagLandlordDocuments(user: AppUser) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Reason (e.g. expired ID, mismatched property doc)"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Flag ${user.fullName}'s documents?")
            .setMessage("Their account will be blocked from posting/managing listings until they resubmit and are re-approved.")
            .setView(input)
            .setPositiveButton("Flag") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Documents could not be verified" }
                viewModel.rejectLandlord(user.uid, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLandlordDocuments(user: AppUser) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        fun docRow(label: String, link: String): android.widget.TextView {
            return android.widget.TextView(requireContext()).apply {
                text = if (link.isBlank()) "$label\nNot provided" else "$label\nTap to open →"
                setTextColor(
                    if (link.isBlank())
                        android.graphics.Color.parseColor("#9E9E9E")
                    else android.graphics.Color.parseColor("#2E7D32")
                )
                textSize = 15f
                setPadding(dp(4), dp(12), dp(4), dp(12))
                if (link.isNotBlank()) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openLinkIfValid(link) }
                }
            }
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(docRow("National ID Document", user.nationalIdDocLink))
            addView(android.view.View(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                )
                setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            })
            addView(docRow(
                "Property Document (${user.propertyDocType.ifBlank { "type not specified" }})",
                user.propertyDocLink
            ))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${user.fullName} — Documents")
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    /**
     * Students aren't individually gatekept at registration (too hectic to
     * review every signup), so this gives admin a way to spot-check school
     * ID documents whenever they have time, and suspend anyone whose ID
     * looks fake after the fact.
     */
    private fun showStudentOptions(user: AppUser) {
        val options = mutableListOf("View School ID")
        options.add(if (user.idFlagged) "Unflag ID" else "Flag ID")
        options.add(if (user.suspended) "Unsuspend" else "Suspend")

        showOptionsDialog(user.fullName, options) { selected ->
            when (selected) {
                "View School ID" -> {
                    if (user.studentIdDocLink.isBlank()) {
                        binding.root.showSnackbar("No school ID document on file")
                    } else {
                        openLinkIfValid(user.studentIdDocLink)
                    }
                }
                "Flag ID" -> promptFlagId(user)
                "Unflag ID" -> promptUnflagId(user)
                "Suspend" -> promptSuspend(user)
                "Unsuspend" -> promptUnsuspend(user)
            }
        }
    }

    /**
     * Flagging is separate from suspension — the student stays able to log
     * in and use the app, they just get routed through a resubmit screen
     * for their school ID until it's fixed.
     */
    private fun promptFlagId(user: AppUser) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Reason (e.g. blurry photo, wrong document)"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Flag ${user.fullName}'s school ID?")
            .setMessage("They'll be prompted to resubmit next time they log in. This does not suspend their account.")
            .setView(input)
            .setPositiveButton("Flag") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Document could not be verified" }
                viewModel.flagStudentId(user.uid, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptUnflagId(user: AppUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${user.fullName}'s ID is flagged")
            .setMessage("Flag reason: ${user.idFlagReason.ifBlank { "Not specified" }}\n\nClear this flag?")
            .setPositiveButton("Unflag") { _, _ -> viewModel.unflagStudentId(user.uid) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openLinkIfValid(link: String) {
        if (link.isBlank()) {
            binding.root.showSnackbar("No document link on file")
            return
        }
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link)))
        } catch (_: Exception) {
            binding.root.showSnackbar("Couldn't open that link")
        }
    }

    private fun promptSuspend(user: AppUser) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Reason for suspension"
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Suspend ${user.fullName}?")
            .setMessage("The user will be blocked from logging in. Their listings will be hidden from students.")
            .setView(input)
            .setPositiveButton("Suspend") { _, _ ->
                val reason = input.text.toString().trim().ifBlank { "Account suspended by admin" }
                viewModel.suspendUser(user.uid, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptUnsuspend(user: AppUser) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${user.fullName} is suspended")
            .setMessage("Suspension reason: ${user.suspensionReason.ifBlank { "Not specified" }}\n\nDo you want to unsuspend this user?")
            .setPositiveButton("Unsuspend") { _, _ -> viewModel.unsuspendUser(user.uid) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    inner class UserAdapter(
        private val onCardClick: (AppUser) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.VH>() {

        private val items = mutableListOf<AppUser>()
        fun submitList(list: List<AppUser>) { items.clear(); items.addAll(list); notifyDataSetChanged() }

        inner class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount() = items.size

        private fun buildDatesText(u: AppUser): String {
            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            val joined = "Joined ${fmt.format(java.util.Date(u.createdAt))}"

            return when {
                u.role == UserRole.LANDLORD && u.verificationStatus == VerificationStatus.VERIFIED && u.verifiedAt != null ->
                    "$joined · Verified ${fmt.format(java.util.Date(u.verifiedAt))}"
                u.role == UserRole.LANDLORD && u.verificationStatus == VerificationStatus.REJECTED && u.rejectedAt != null ->
                    "$joined · Rejected ${fmt.format(java.util.Date(u.rejectedAt))}"
                else -> joined
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val u = items[position]; val b = holder.b

            b.tvName.text  = u.fullName
            b.tvEmail.text = u.email
            b.tvPhone.text = u.phone.ifBlank { "No phone recorded" }

            // Dim suspended users
            holder.itemView.alpha = if (u.suspended) 0.5f else 1.0f
            holder.itemView.setOnClickListener { onCardClick(u) }

            b.tvDates.text = buildDatesText(u)

            when (u.role) {
                UserRole.STUDENT -> {
                    b.tvRoleBadge.text = "STUDENT"
                    b.tvRoleBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1565C0"))
                    b.tvDetail.text = if (u.campus.isNotBlank()) "📍 ${u.campus}" else ""
                    b.tvVerification.visibility = View.GONE
                }
                UserRole.LANDLORD -> {
                    b.tvRoleBadge.text = "LANDLORD"
                    b.tvRoleBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                    b.tvDetail.text = if (u.operatingLocation.isNotBlank()) "📍 ${u.operatingLocation}" else ""
                    b.tvVerification.visibility = View.VISIBLE
                    when {
                        u.suspended -> {
                            b.tvVerification.text = "🚫 SUSPENDED"
                            b.tvVerification.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#37474F"))
                            b.tvVerification.setTextColor(Color.WHITE)
                        }
                        u.verificationStatus == VerificationStatus.VERIFIED -> {
                            b.tvVerification.text = "✓ VERIFIED"
                            b.tvVerification.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                            b.tvVerification.setTextColor(Color.WHITE)
                        }
                        u.verificationStatus == VerificationStatus.PENDING -> {
                            b.tvVerification.text = "⏳ PENDING"
                            b.tvVerification.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F57F17"))
                            b.tvVerification.setTextColor(Color.WHITE)
                        }
                        else -> {
                            b.tvVerification.text = "✗ REJECTED"
                            b.tvVerification.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))
                            b.tvVerification.setTextColor(Color.WHITE)
                        }
                    }
                }
                UserRole.ADMIN -> {
                    b.tvRoleBadge.text = "ADMIN"
                    b.tvRoleBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#B71C1C"))
                    b.tvDetail.text = ""
                    b.tvVerification.visibility = View.GONE
                }
            }

            // Show suspended badge for students too
            if (u.suspended && u.role == UserRole.STUDENT) {
                b.tvVerification.visibility = View.VISIBLE
                b.tvVerification.text = "🚫 SUSPENDED"
                b.tvVerification.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#37474F"))
                b.tvVerification.setTextColor(Color.WHITE)
            }
        }
    }
}