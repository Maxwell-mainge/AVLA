package com.avla.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.databinding.FragmentLandlordPendingBinding

/**
 * Shown to a landlord who has registered but is not yet verified.
 * If rejected, shows reason and a "Resubmit Documents" button.
 */
class LandlordPendingFragment : Fragment() {

    private var _binding: FragmentLandlordPendingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLandlordPendingBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("SetTextI18n") // Suppresses lint warnings about hardcoded text literals
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isRejected      = arguments?.getBoolean("IS_REJECTED", false) ?: false
        val rejectionReason = arguments?.getString("REJECTION_REASON") ?: ""

        if (isRejected) {
            binding.tvStatus.text = "Your verification was rejected"
            binding.tvReason.visibility = View.VISIBLE
            binding.tvReason.text = "Reason: $rejectionReason"
            binding.btnResubmit.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = "Your account is under review"
            binding.tvReason.visibility = View.GONE
            binding.btnResubmit.visibility = View.GONE
        }

        binding.btnResubmit.setOnClickListener {
            // Resubmit mode: the landlord already has an account, so the
            // register form pre-fills their existing details and only lets
            // them fix documents/location — it no longer tries to create a
            // brand-new Firebase account with the same email.
            val bundle = Bundle().apply { putBoolean("RESUBMIT_MODE", true) }
            findNavController().navigate(R.id.landlordRegisterFragment, bundle)
        }

        binding.btnLogout.setOnClickListener {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()

            // Safely drops backward through the navigation stack directly to the login fragment layout
            val successfullyPopped = findNavController().popBackStack(R.id.loginFragment, false)

            // Fallback: If login wasn't in our backstack history, jump straight to its explicit destination ID
            if (!successfullyPopped) {
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}