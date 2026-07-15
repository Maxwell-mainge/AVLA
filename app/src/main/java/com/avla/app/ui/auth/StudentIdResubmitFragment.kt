package com.avla.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.databinding.FragmentStudentIdResubmitBinding
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Shown to a student whose school ID was flagged on admin spot-check.
 * Unlike LandlordPendingFragment, this never blocks app usage entirely —
 * but unlike before, resubmitting a new link does NOT drop the student
 * straight into MainActivity. It stays flagged until an admin explicitly
 * reviews the resubmission (see FirebaseRepository.reviewResubmittedStudentId).
 *
 * ID_RESUBMITTED argument (passed via AuthActivity from LoginFragment) tells
 * this screen whether the student already resubmitted and is just waiting —
 * in that case we show the "under review" message instead of the form again.
 */
class StudentIdResubmitFragment : Fragment() {

    private var _binding: FragmentStudentIdResubmitBinding? = null
    private val binding get() = _binding!!
    private val repo = FirebaseRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStudentIdResubmitBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reason = arguments?.getString("ID_FLAG_REASON") ?: ""
        binding.tvReason.text = "Reason: ${reason.ifBlank { "Not specified" }}"

        val alreadyResubmitted = arguments?.getBoolean("ID_RESUBMITTED") ?: false
        if (alreadyResubmitted) {
            showUnderReviewState()
        }

        binding.btnOpenDrive.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://drive.google.com/drive/my-drive")
                )
            )
        }

        binding.btnResubmit.setOnClickListener {
            val newLink = binding.etStudentIdLink.text.toString().trim()
            if (newLink.isBlank()) {
                binding.root.showSnackbar("Paste your new student ID document link")
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                binding.root.showSnackbar("Not signed in")
                return@setOnClickListener
            }

            binding.btnResubmit.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    repo.resubmitStudentId(uid, newLink)
                    if (_binding == null) return@launch

                    // No longer launches MainActivity — the student stays flagged
                    // until an admin reviews the resubmission. Show the waiting
                    // state in place instead.
                    binding.root.showSnackbar("Resubmitted. An admin will review it shortly.")
                    showUnderReviewState()
                } catch (e: Exception) {
                    if (_binding != null) {
                        binding.btnResubmit.isEnabled = true
                        binding.root.showSnackbar(e.message ?: "Failed to resubmit. Try again.")
                    }
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val successfullyPopped = findNavController().popBackStack(R.id.loginFragment, false)
            if (!successfullyPopped) {
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    @Suppress("SetTextI18n")
    private fun showUnderReviewState() {
        binding.llResubmitForm.visibility = View.GONE
        binding.tvUnderReview.visibility = View.VISIBLE
        binding.tvTitle.text = "Resubmission under review"
        binding.tvSubtitle.text = "We've received your resubmitted document."
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}