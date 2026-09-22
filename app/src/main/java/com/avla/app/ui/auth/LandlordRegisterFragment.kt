package com.avla.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.databinding.FragmentLandlordRegisterBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.avla.app.utils.validateEmail
import com.avla.app.utils.validateKenyanPhone
import com.avla.app.utils.validateNationalId
import com.avla.app.utils.validatePassword

class LandlordRegisterFragment : Fragment() {

    private var _binding: FragmentLandlordRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    // True when opened via "Resubmit Documents" from LandlordPendingFragment,
    // for a landlord who already has an account — not a brand-new signup.
    private var isResubmitMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLandlordRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isResubmitMode = arguments?.getBoolean("RESUBMIT_MODE", false) ?: false

        if (isResubmitMode) {
            setupResubmitMode()
        } else {
            binding.btnRegister.setOnClickListener { attemptRegister() }
        }

        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        val openDrive = {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://drive.google.com/drive/my-drive")
                )
            )
        }
        binding.btnOpenDriveNationalId.setOnClickListener { openDrive() }
        binding.btnOpenDrivePropertyDoc.setOnClickListener { openDrive() }

        observeState()
    }

    /**
     * Resubmit mode: the landlord already has an account, so account-identity
     * fields (name, email, phone, national ID, password) are locked and
     * pre-filled — only the documents/location they need to fix are editable.
     */
    private fun setupResubmitMode() {
        binding.etFullName.isEnabled = false
        binding.etEmail.isEnabled = false
        binding.etPhone.isEnabled = false
        binding.etNationalId.isEnabled = false
        binding.tilPassword.visibility = View.GONE

        binding.btnRegister.text = "Resubmit for Review"
        binding.btnRegister.setOnClickListener { attemptResubmit() }

        viewModel.loadCurrentUserProfile()
        viewModel.profileState.observe(viewLifecycleOwner) { state ->
            if (state is UiState.Success) {
                val user = state.data
                binding.etFullName.setText(user.fullName)
                binding.etEmail.setText(user.email)
                binding.etPhone.setText(user.phone)
                binding.etNationalId.setText(user.nationalId)
                binding.etOperatingLocation.setText(user.operatingLocation)
                binding.etNationalIdLink.setText(user.nationalIdDocLink)
                binding.etPropertyDocLink.setText(user.propertyDocLink)
            } else if (state is UiState.Error) {
                binding.root.showSnackbar(state.message)
            }
        }

        viewModel.resubmitState.observe(viewLifecycleOwner) { state ->
            binding.btnRegister.isEnabled = state !is UiState.Loading
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> {
                    binding.root.showSnackbar("Documents resubmitted — please log in again once reviewed")
                    viewModel.logout()
                    val popped = findNavController().popBackStack(R.id.loginFragment, false)
                    if (!popped) findNavController().navigate(R.id.loginFragment)
                }
                is UiState.Error -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    private fun attemptResubmit() {
        val location = binding.etOperatingLocation.text.toString().trim()
        val idLink    = binding.etNationalIdLink.text.toString().trim()
        val docLink   = binding.etPropertyDocLink.text.toString().trim()
        val docType   = binding.spinnerDocType.selectedItem.toString()

        var valid = true
        if (location.isBlank()) { binding.tilOperatingLocation.error = "Enter area you operate in"; valid = false }
        if (idLink.isBlank())   { binding.tilNationalIdLink.error    = "Paste your National ID document link"; valid = false }
        if (docLink.isBlank()) { binding.tilPropertyDocLink.error    = "Paste your property document link"; valid = false }
        if (!valid) return

        binding.tilOperatingLocation.error = null
        binding.tilNationalIdLink.error = null
        binding.tilPropertyDocLink.error = null

        viewModel.resubmitLandlordDocuments(
            operatingLocation = location,
            nationalIdDocLink = idLink,
            propertyDocLink   = docLink,
            propertyDocType   = docType
        )
    }

    private fun attemptRegister() {
        val fullName   = binding.etFullName.text.toString().trim()
        val email      = binding.etEmail.text.toString().trim()
        val phone      = binding.etPhone.text.toString().trim()
        val nationalId = binding.etNationalId.text.toString().trim()
        val location   = binding.etOperatingLocation.text.toString().trim()
        val password   = binding.etPassword.text.toString()
        val idLink     = binding.etNationalIdLink.text.toString().trim()
        val docLink    = binding.etPropertyDocLink.text.toString().trim()
        val docType    = binding.spinnerDocType.selectedItem.toString()

        var valid = true
        if (fullName.isBlank())              { binding.tilFullName.error         = "Required"; valid = false }
        if (!validateEmail(email))            { binding.tilEmail.error            = "Invalid email"; valid = false }
        if (!validateKenyanPhone(phone))      { binding.tilPhone.error            = "Invalid number e.g. 0712345678"; valid = false }
        if (!validateNationalId(nationalId))  { binding.tilNationalId.error       = "Enter valid 7-8 digit ID"; valid = false }
        if (location.isBlank())              { binding.tilOperatingLocation.error = "Enter area you operate in"; valid = false }
        if (!validatePassword(password))      { binding.tilPassword.error         = "Minimum 6 characters"; valid = false }
        if (idLink.isBlank())                { binding.tilNationalIdLink.error    = "Paste your National ID document link"; valid = false }
        if (docLink.isBlank())               { binding.tilPropertyDocLink.error   = "Paste your property document link"; valid = false }
        if (!valid) return

        listOf(binding.tilFullName, binding.tilEmail, binding.tilPhone, binding.tilNationalId,
            binding.tilOperatingLocation, binding.tilPassword, binding.tilNationalIdLink,
            binding.tilPropertyDocLink).forEach { it.error = null }

        viewModel.registerLandlord(
            fullName          = fullName,
            email             = email,
            phone             = phone,
            nationalId        = nationalId,
            operatingLocation = location,
            password          = password,
            nationalIdDocLink = idLink,
            propertyDocLink   = docLink,
            propertyDocType   = docType
        )
    }

    private fun observeState() {
        // Only relevant for the fresh-registration path — resubmit uses its
        // own resubmitState observer set up in setupResubmitMode().
        if (isResubmitMode) return

        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.btnRegister.isEnabled  = state !is UiState.Loading
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> findNavController().navigate(R.id.verifyEmailFragment)
                is UiState.Error   -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}