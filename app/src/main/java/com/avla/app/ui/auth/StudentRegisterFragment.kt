package com.avla.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.databinding.FragmentStudentRegisterBinding
import com.avla.app.utils.UiState
import com.avla.app.utils.showSnackbar
import com.avla.app.utils.validateEmail
import com.avla.app.utils.validateKenyanPhone
import com.avla.app.utils.validateNationalId
import com.avla.app.utils.validatePassword

class StudentRegisterFragment : Fragment() {

    private var _binding: FragmentStudentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStudentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }
        binding.btnOpenDriveStudentId.setOnClickListener {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://drive.google.com/drive/my-drive")
                )
            )
        }
    }

    private fun attemptRegister() {
        val fullName      = binding.etFullName.text.toString().trim()
        val email         = binding.etEmail.text.toString().trim()
        val phone         = binding.etPhone.text.toString().trim()
        val nationalId    = binding.etNationalId.text.toString().trim()
        val campus        = binding.etCampus.text.toString().trim()
        val studentIdLink = binding.etStudentIdLink.text.toString().trim()
        val password      = binding.etPassword.text.toString()

        var valid = true
        if (fullName.isBlank())              { binding.tilFullName.error      = "Required"; valid = false }
        if (!validateEmail(email))            { binding.tilEmail.error         = "Invalid email"; valid = false }
        if (!validateKenyanPhone(phone))      { binding.tilPhone.error         = "Invalid number e.g. 0712345678"; valid = false }
        if (!validateNationalId(nationalId))  { binding.tilNationalId.error    = "Enter valid 7-8 digit ID"; valid = false }
        if (campus.isBlank())                { binding.tilCampus.error        = "Enter your university/college"; valid = false }
        if (studentIdLink.isBlank())         { binding.tilStudentIdLink.error = "Paste your student ID document link"; valid = false }
        if (!validatePassword(password))      { binding.tilPassword.error      = "Minimum 6 characters"; valid = false }
        if (!valid) return

        listOf(binding.tilFullName, binding.tilEmail, binding.tilPhone,
            binding.tilNationalId, binding.tilCampus, binding.tilStudentIdLink,
            binding.tilPassword).forEach { it.error = null }

        viewModel.registerStudent(
            fullName         = fullName,
            email            = email,
            phone            = phone,
            nationalId       = nationalId,
            campus           = campus,
            studentIdDocLink = studentIdLink,
            password         = password
        )
    }

    private fun observeState() {
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            binding.btnRegister.isEnabled  = state !is UiState.Loading
            binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is UiState.Success -> findNavController().navigate(R.id.loginFragment)
                is UiState.Error   -> binding.root.showSnackbar(state.message)
                else -> Unit
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}