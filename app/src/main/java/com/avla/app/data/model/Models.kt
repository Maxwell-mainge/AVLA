package com.avla.app.data.model

import com.google.firebase.firestore.PropertyName

enum class UserRole { STUDENT, LANDLORD, ADMIN }
enum class VerificationStatus { PENDING, VERIFIED, REJECTED }

enum class PropertyType {
    BEDSITTER, STUDIO, ONE_BEDROOM, TWO_BEDROOM, THREE_BEDROOM;
    fun displayName() = when (this) {
        BEDSITTER     -> "Bedsitter"
        STUDIO        -> "Studio"
        ONE_BEDROOM   -> "1 Bedroom"
        TWO_BEDROOM   -> "2 Bedrooms"
        THREE_BEDROOM -> "3 Bedrooms"
    }
}

data class AppUser(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val nationalId: String = "",
    val role: UserRole = UserRole.STUDENT,
    val isEmailVerified: Boolean = false,
    val campus: String = "",
    val campusLatLng: String = "",
    val studentIdDocLink: String = "",
    val operatingLocation: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING,
    val nationalIdDocLink: String = "",
    val propertyDocLink: String = "",
    val propertyDocType: String = "",
    val rejectionReason: String = "",
    val suspended: Boolean = false,         // admin can suspend any user
    val suspensionReason: String = "",      // reason shown to suspended user
    val idFlagged: Boolean = false,         // admin spot-checked a student's school ID and it looked invalid
    val idFlagReason: String = "",          // reason shown to the student, separate from suspension
    val idResubmitted: Boolean = false,     // NEW — student resubmitted a new doc link; stays flagged until admin re-checks
    val createdAt: Long = System.currentTimeMillis(),
    val verifiedAt: Long? = null,           // when a landlord was approved
    val rejectedAt: Long? = null,           // when a landlord was last rejected
    val favoriteListingIds: List<String> = emptyList(),
    val fcmToken: String = ""               // NEW — this device's push notification token
)

data class Listing(
    val id: String = "",
    val landlordUid: String = "",
    val landlordName: String = "",
    val landlordPhone: String = "",
    val title: String = "",
    val description: String = "",
    val propertyType: PropertyType = PropertyType.BEDSITTER,
    val priceKsh: Long = 0,
    val bedrooms: Int = 1,
    val location: String = "",
    val nearCampus: String = "",
    val distanceKm: Double = 0.0,
    val amenities: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    @get:PropertyName("available") @set:PropertyName("available") var available: Boolean = true,
    @get:PropertyName("mockData")  @set:PropertyName("mockData")  var mockData: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val favoriteListingIds: List<String> = emptyList(),
)
{
}

data class ListingFilter(
    val minPriceKsh: Long = 0,
    val maxPriceKsh: Long = 50_000,
    val propertyType: PropertyType? = null,
    val location: String = "",
    val landlordUid: String? = null
)