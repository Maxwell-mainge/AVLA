package com.avla.app.data.model

import com.google.firebase.Timestamp

/**
 * One row in the "activity" Firestore collection.
 * Written whenever something admin-relevant happens (landlord registers,
 * listing gets published, verification approved, etc.).
 */
data class ActivityLog(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Timestamp? = null
)