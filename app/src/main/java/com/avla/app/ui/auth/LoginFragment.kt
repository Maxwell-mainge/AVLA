package com.avla.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.avla.app.databinding.FragmentLoginBinding
import com.avla.app.ui.admin.AdminActivity
import com.avla.app.ui.main.MainActivity
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.avla.app.utils.validateEmail
import com.avla.app.utils.validatePassword
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
        observeResetPasswordState()
        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvRegister.setOnClickListener {
            navigateToRegister()
        }
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    /**
     * Routes "Create an account" to the correct registration form based on
     * which role button was tapped on the previous screen. Falls back to
     * role selection if this screen was reached some other way (e.g. no
     * PENDING_ROLE argument present).
     */
    private fun navigateToRegister() {
        when (arguments?.getString("PENDING_ROLE")) {
            "STUDENT" -> findNavController().navigate(R.id.studentRegisterFragment)
            "LANDLORD" -> findNavController().navigate(R.id.landlordRegisterFragment)
            else -> findNavController().navigate(R.id.roleSelectFragment)
        }
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Enter your registered email"
            setPadding(48, 24, 48, 24)
            // Pre-fill with whatever they've already typed in the login form
            setText(binding.etEmail.text.toString().trim())
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Password")
            .setMessage("We'll send a password reset link to your email.")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (!validateEmail(email)) {
                    binding.root.showSnackbar("Enter a valid email address")
                } else {
                    viewModel.sendPasswordReset(email)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeResetPasswordState() {
        viewModel.resetPasswordState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Success -> binding.root.showSnackbar(state.data)
                is UiState.Error   -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    private fun attemptLogin() {
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (!validateEmail(email))    { binding.tilEmail.error    = "Invalid email"; return }
        if (!validatePassword(password)) { binding.tilPassword.error = "Min 6 characters"; return }
        binding.tilEmail.error    = null
        binding.tilPassword.error = null
        viewModel.login(email, password)
    }

    private fun observeState() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.btnLogin.isEnabled     = state !is UiState.Loading
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE

            when (state) {
                is UiState.Success -> {
                    val user = state.data

                    // Block suspended users immediately
                    if (user.suspended) {
                        FirebaseAuth.getInstance().signOut()
                        val reason = user.suspensionReason.ifBlank { "Please contact support." }

                        val fullText = "Reason: $reason\n\n" +
                                "If you believe this was a mistake, contact AVLA support:\n" +
                                "Email: admin@test.com\n" +
                                "Phone: 0700000000"
                        val spannable = android.text.SpannableString(fullText)

                        val emailStart = fullText.indexOf("admin@test.com")
                        spannable.setSpan(
                            object : android.text.style.ClickableSpan() {
                                override fun onClick(widget: View) {
                                    startActivity(
                                        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:admin@test.com"))
                                    )
                                }
                            },
                            emailStart, emailStart + "admin@test.com".length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        val phoneStart = fullText.indexOf("0700000000")
                        spannable.setSpan(
                            object : android.text.style.ClickableSpan() {
                                override fun onClick(widget: View) {
                                    startActivity(
                                        Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:0700000000"))
                                    )
                                }
                            },
                            phoneStart, phoneStart + "0700000000".length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        val messageView = android.widget.TextView(requireContext()).apply {
                            text = spannable
                            movementMethod = android.text.method.LinkMovementMethod.getInstance()
                            setPadding(64, 32, 64, 8)
                            setLinkTextColor(android.graphics.Color.parseColor("#2E7D32"))
                            setTextColor(android.graphics.Color.parseColor("#616161"))
                            textSize = 14f
                        }

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Account Suspended")
                            .setView(messageView)
                            .setPositiveButton("OK", null)
                            .setCancelable(false)
                            .show()
                        return@observe
                    }

                    // Enforce that whoever tapped "Student" on the previous screen
                    // can only log into a Student account, and likewise for
                    // Landlord — admin is exempt since they don't come through
                    // role selection at all.
                    val pendingRole = arguments?.getString("PENDING_ROLE")
                    if (pendingRole != null && user.role != UserRole.ADMIN && user.role.name != pendingRole) {
                        FirebaseAuth.getInstance().signOut()
                        val actualRoleLabel = if (user.role == UserRole.STUDENT) "Student" else "Landlord"
                        binding.root.showSnackbar(
                            "This account is registered as a $actualRoleLabel. Go back and select $actualRoleLabel to log in."
                        )
                        return@observe
                    }

                    // Flagged students aren't blocked from the app — they're routed
                    // through a resubmit screen first, same pattern as landlord
                    // verification. Checked only after suspension, since suspension
                    // is the harder block and always wins if both are true.
                    // ID_RESUBMITTED is passed along so the resubmit screen knows
                    // whether to show the form or the "under review" waiting state.
                    if (user.role == UserRole.STUDENT && user.idFlagged) {
                        val destination = Intent(requireContext(), AuthActivity::class.java).apply {
                            putExtra("SHOW_ID_FLAGGED", true)
                            putExtra("ID_FLAG_REASON", user.idFlagReason)
                            putExtra("ID_RESUBMITTED", user.idResubmitted)
                        }
                        startActivity(destination)
                        requireActivity().finish()
                        return@observe
                    }

                    val destination = when {
                        user.role == UserRole.ADMIN -> {
                            Intent(requireContext(), AdminActivity::class.java)
                        }
                        user.role == UserRole.LANDLORD &&
                                user.verificationStatus != VerificationStatus.VERIFIED -> {
                            Intent(requireContext(), AuthActivity::class.java).apply {
                                putExtra("SHOW_PENDING", true)
                                putExtra("REJECTION_REASON", user.rejectionReason)
                                putExtra("IS_REJECTED", user.verificationStatus == VerificationStatus.REJECTED)
                            }
                        }
                        else -> {
                            Intent(requireContext(), MainActivity::class.java).apply {
                                putExtra("USER_ROLE", user.role.name)
                                putExtra("USER_CAMPUS", user.campus)
                            }
                        }
                    }
                    startActivity(destination)
                    requireActivity().finish()
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}