package com.avla.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.avla.app.databinding.FragmentAboutBinding

/**
 * Static About/Help screen — shared across student, landlord, and admin.
 * Content is hardcoded (no Firestore) since it's fixed reference info.
 */
class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSupportEmail.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:admin@test.com")
            })
        }

        binding.tvSupportPhone.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:0700000000")))
        }

        binding.linkCa.setOnClickListener {
            openLink("https://ca.go.ke")
        }
        binding.linkFirebase.setOnClickListener {
            openLink("https://firebase.google.com/docs")
        }
        binding.linkRightmove.setOnClickListener {
            openLink("https://www.rightmove.co.uk")
        }
        binding.linkHauzisha.setOnClickListener {
            openLink("https://www.hauzisha.co.ke")
        }
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}