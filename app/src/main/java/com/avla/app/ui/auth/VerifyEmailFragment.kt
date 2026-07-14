package com.avla.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.databinding.FragmentVerifyEmailBinding
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth

/**
 * Repurposed to show a message that the account has been created
 * and is waiting for admin verification.
 */
class VerifyEmailFragment : Fragment() {

    private var _binding: FragmentVerifyEmailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerifyEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the custom text fields programmatically to override the default layout strings
        binding.tvTitle.text = "Registration Successful"
        binding.tvMessage.text = "Your documentation has been submitted successfully! Please log out and sign back in once the administrator completes your profile validation."
        binding.btnContinue.text = "Check Review Status"
        binding.tvResend.text = "Sign Out to Login Screen"

        // 🔄 Button 1: Prompt them to sign out to refresh status safely
        binding.btnContinue.setOnClickListener {
            binding.root.showSnackbar("Please sign out and sign back in to reload your validation records.")
        }

        // 🚪 Button 2: FIXED CLICK HANDLER - Clear Firebase Auth session and force jump back to Login
        binding.tvResend.setOnClickListener {
            // 1. Manually trigger a safety sign out to ensure cache session state resets
            FirebaseAuth.getInstance().signOut()
            viewModel.logout()

            // 2. Clear stack navigation history back to the main login prompt layout target
            val successfullyPopped = findNavController().popBackStack(R.id.loginFragment, false)

            // 🔍 Fallback: If login fragment layout is missing from history, force navigate directly onto it
            if (!successfullyPopped) {
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
