package com.avla.app.ui.profile

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentProfileBinding
import com.avla.app.ui.auth.AuthActivity
import com.avla.app.utils.showSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            val user = repo.getUserProfile(uid) ?: return@launch

            binding.tvName.text = user.fullName
            binding.tvEmail.text = user.email
            binding.tvPhone.text = user.phone
            binding.tvRole.text = user.role.name

            if (user.role == UserRole.STUDENT) {
                binding.llStudentInfo?.visibility = View.VISIBLE
                binding.llLandlordInfo?.visibility = View.GONE

                binding.tvCampus?.text = "Campus: ${user.campus}"
                binding.tvNationalId?.text = "National ID: ${user.nationalId}"

                // 🚀 Updated to tint the student avatar green instead of blue
                binding.ivProfileAvatar.setImageResource(R.drawable.ic_student_profile)
                binding.ivProfileAvatar.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.avla_green)
                )
                binding.ivProfileAvatar.visibility = View.VISIBLE

            } else if (user.role == UserRole.LANDLORD) {
                binding.llStudentInfo?.visibility = View.GONE
                binding.llLandlordInfo?.visibility = View.VISIBLE

                binding.tvOperatingLocation?.text = "Operating In: ${user.operatingLocation}"
                binding.tvLandlordNationalId?.text = "National ID: ${user.nationalId}"

                val statusText = when (user.verificationStatus) {
                    VerificationStatus.VERIFIED -> "Verified Account"
                    VerificationStatus.PENDING -> "Under Review"
                    VerificationStatus.REJECTED -> "Rejected: ${user.rejectionReason}"
                }
                binding.tvVerificationStatus?.text = "Status: $statusText"

                binding.ivProfileAvatar.setImageResource(R.drawable.ic_landlord_profile)
                binding.ivProfileAvatar.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.avla_green)
                )
                binding.ivProfileAvatar.visibility = View.VISIBLE
            }
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnAbout.setOnClickListener {
            findNavController().navigate(R.id.aboutFragment)
        }

        binding.btnLogout.setOnClickListener {
            repo.logout()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun showChangePasswordDialog() {
        val context = requireContext()

        val currentPasswordInput = EditText(context).apply {
            hint = "Current password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }
        val newPasswordInput = EditText(context).apply {
            hint = "New password (min 6 characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 24, 48, 24)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(currentPasswordInput)
            addView(newPasswordInput)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Change Password")
            .setView(container)
            .setPositiveButton("Update") { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()

                if (currentPassword.isBlank() || newPassword.isBlank()) {
                    binding.root.showSnackbar("Both fields are required")
                    return@setPositiveButton
                }
                if (newPassword.length < 6) {
                    binding.root.showSnackbar("New password must be at least 6 characters")
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        repo.changePassword(currentPassword, newPassword)
                        if (_binding != null) binding.root.showSnackbar("Password updated successfully")
                    } catch (e: Exception) {
                        if (_binding != null) {
                            binding.root.showSnackbar(e.message ?: "Failed to update password")
                        }
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