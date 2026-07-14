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
import com.avla.app.ui.main.MainActivity
import com.avla.app.utils.showSnackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Shown to a student whose school ID was flagged on admin spot-check.
 * Unlike LandlordPendingFragment, this never blocks app usage — once the
 * student resubmits a valid link, they go straight into MainActivity
 * rather than being sent back to log in again.
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
                    val user = repo.getUserProfile(uid)
                    if (_binding == null) return@launch

                    startActivity(
                        Intent(requireContext(), MainActivity::class.java).apply {
                            putExtra("USER_ROLE", "STUDENT")
                            putExtra("USER_CAMPUS", user?.campus ?: "")
                        }
                    )
                    requireActivity().finish()
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}