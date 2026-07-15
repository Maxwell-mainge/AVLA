package com.avla.app.ui.listings

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.avla.app.R
import com.avla.app.data.model.Listing
import com.avla.app.databinding.FragmentLandlordManageListingBinding
import com.avla.app.utils.convertDriveLinkToDirectUrl
import com.avla.app.utils.showSnackbar
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore

class LandlordManageListingFragment : Fragment() {

    private var _binding: FragmentLandlordManageListingBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordManageListingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val listingId = arguments?.getString("listingId") ?: ""
        if (listingId.isNotBlank()) {
            loadPropertyData(listingId)
        } else {
            binding.root.showSnackbar("Listing identifier missing")
        }
    }

    private fun loadPropertyData(listingId: String) {
        db.collection("listings").document(listingId).get()
            .addOnSuccessListener { snapshot ->
                val listing = snapshot.toObject(Listing::class.java) ?: return@addOnSuccessListener
                setupUI(listing)
            }
            .addOnFailureListener {
                binding.root.showSnackbar("Failed to fetch property details")
            }
    }

    @Suppress("SetTextI18n")
    private fun setupUI(listing: Listing) {
        binding.tvManageTitle.text = "Manage: ${listing.title}"
        binding.etPrice.setText(listing.priceKsh.toString())

        binding.switchAvailability.isChecked = listing.available
        updateStatusSubtext(listing.available)

        binding.switchAvailability.setOnCheckedChangeListener { _, isChecked: Boolean ->
            updateStatusSubtext(isChecked)
            db.collection("listings").document(listing.id)
                .update("available", isChecked)
                .addOnFailureListener { binding.root.showSnackbar("Failed to update status") }
        }

        binding.btnSave.setOnClickListener {
            val newPrice = binding.etPrice.text.toString().toIntOrNull()
            if (newPrice == null || newPrice <= 0) {
                binding.root.showSnackbar("Please enter a valid price amount")
                return@setOnClickListener
            }

            db.collection("listings").document(listing.id)
                .update("priceKsh", newPrice)
                .addOnSuccessListener {
                    binding.root.showSnackbar("Price changes saved successfully!")
                }
                .addOnFailureListener { binding.root.showSnackbar("Failed to save changes") }
        }

        // Photo Link Editor Popup Dialog Logic with Live Previews
        binding.btnEditPhotos.setOnClickListener {
            val context = requireContext()

            val containerLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 32, 48, 32)
            }

            val borderDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setStroke(3, android.graphics.Color.parseColor("#CCCCCC"))
                setColor(android.graphics.Color.WHITE)
            }

            // Generate 5 rows. Each row contains an EditText field and a clickable Preview Eye Icon
            val editTextFields = ArrayList<EditText>()

            for (index in 0 until 5) {
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { lp -> lp.setMargins(0, 0, 0, 24) }
                }

                val editText = EditText(context).apply {
                    hint = "Google Drive Photo Link ${index + 1}"
                    setText(listing.imageUrls.getOrNull(index) ?: "")
                    inputType = InputType.TYPE_TEXT_VARIATION_URI
                    background = borderDrawable
                    setPadding(32, 24, 32, 24)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                editTextFields.add(editText)

                // Shortcut to open Google Drive's website directly, since users
                // often need to upload/locate the file there before they have
                // a link to paste back into this field. Labeled, not a bare
                // icon, so its function is obvious at a glance.
                val driveButton = com.google.android.material.button.MaterialButton(
                    context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    text = "Drive"
                    textSize = 11f
                    isAllCaps = false
                    icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_upload)
                    iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E7D32"))
                    setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E7D32"))
                    setPadding(8, 4, 8, 4)
                    setOnClickListener {
                        startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://drive.google.com/drive/my-drive")
                            )
                        )
                    }
                }

                // Built-in standard Android resource eye icon for viewing/previewing
                val previewIcon = ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_menu_view)
                    setPadding(16, 16, 16, 16)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    // NEW — explicit label. This listener is nested inside the
                    // outer btnEditPhotos.setOnClickListener { ... } above, so
                    // both lambdas previously shared the same implicit label
                    // ("setOnClickListener"), which is what made
                    // return@setOnClickListener ambiguous and failed compilation.
                    setOnClickListener preview@{
                        val currentLink = editText.text.toString().trim()
                        if (currentLink.isBlank()) {
                            binding.root.showSnackbar("Please paste a link first to preview it")
                            return@preview
                        }

                        // Open a sub-dialog to show the live parsed Google Drive image thumbnail
                        val previewImage = ImageView(context).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }

                        val directUrl = convertDriveLinkToDirectUrl(currentLink)

                        Glide.with(context)
                            .load(directUrl)
                            .placeholder(R.drawable.ic_placeholder_house)
                            .error(R.drawable.ic_placeholder_house)
                            .into(previewImage)

                        MaterialAlertDialogBuilder(context)
                            .setTitle("Image Preview")
                            .setView(previewImage)
                            .setPositiveButton("Close", null)
                            .show()
                    }
                }

                rowLayout.addView(editText)
                rowLayout.addView(driveButton)
                rowLayout.addView(previewIcon)
                containerLayout.addView(rowLayout)
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("Edit Property Photos")
                .setMessage("Paste up to 5 Google Drive links. Tap the eye icon to preview your images:")
                .setView(containerLayout)
                .setPositiveButton("Save Changes") { _, _ ->
                    val updatedPhotoUrls = editTextFields.map { it.text.toString().trim() }
                        .filter { it.isNotBlank() }

                    db.collection("listings").document(listing.id)
                        .update("imageUrls", updatedPhotoUrls)
                        .addOnSuccessListener {
                            binding.root.showSnackbar("Photos updated successfully!")
                            loadPropertyData(listing.id)
                        }
                        .addOnFailureListener { binding.root.showSnackbar("Failed to save photo updates") }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnDeleteListing.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Permanently Delete?")
                .setMessage("Are you absolutely sure you want to remove this property?")
                .setPositiveButton("Delete") { _, _ ->
                    db.collection("listings").document(listing.id).delete()
                        .addOnSuccessListener {
                            findNavController().popBackStack()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @Suppress("SetTextI18n")
    private fun updateStatusSubtext(isAvailableOnFeed: Boolean) {
        binding.tvStatusSubtext.text = if (isAvailableOnFeed) {
            "Available on student feeds"
        } else {
            "Hidden / Marked as Occupied"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}