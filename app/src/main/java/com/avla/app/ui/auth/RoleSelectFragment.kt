package com.avla.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.databinding.FragmentRoleSelectBinding

class RoleSelectFragment : Fragment() {

    private var _binding: FragmentRoleSelectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRoleSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTagline()

        // Both buttons now go to Login first. The chosen role rides along as
        // an argument so that if the person taps "Create an account" from
        // Login, it opens the correct registration form (Student or Landlord)
        // instead of sending them back to this screen.
        binding.btnStudent.setOnClickListener {
            navigateToLogin("STUDENT")
        }
        binding.btnLandlord.setOnClickListener {
            navigateToLogin("LANDLORD")
        }
    }

    /** "Campus housing, made simple." with "simple." in the accent color. */
    private fun setupTagline() {
        val plainPart = "Campus housing, made "
        val accentPart = "simple."
        val full = plainPart + accentPart

        val spannable = android.text.SpannableString(full)
        val accentColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.avla_green)
        val blackColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.black)

        spannable.setSpan(
            android.text.style.ForegroundColorSpan(blackColor),
            0, plainPart.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(accentColor),
            plainPart.length, full.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvTagline.text = spannable
    }

    private fun navigateToLogin(pendingRole: String) {
        val bundle = Bundle().apply { putString("PENDING_ROLE", pendingRole) }
        findNavController().navigate(R.id.loginFragment, bundle)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}