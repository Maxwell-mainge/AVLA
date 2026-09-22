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

// NEW — status of a single unit reservation within the escrow flow
enum class ReservationStatus { RESERVED, CONFIRMED, REJECTED }

// NEW — type of entry in a landlord's wallet transaction history
// PLATFORM_FEE — NEW — the simulated flat fee a landlord pays to post a
// listing; unlike the other three types, this money doesn't come from or
// go to pendingBalanceKsh/availableBalanceKsh at all — it's platform
// revenue, not landlord earnings, so it's logged here purely for visibility
// in the Admin's platform-wide Transactions feed.
enum class TransactionType { DEPOSIT_HELD, SETTLED, REFUNDED, WITHDRAWN, PLATFORM_FEE }

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
    val fcmToken: String = "",              // NEW — this device's push notification token
    val pendingBalanceKsh: Long = 0,        // NEW — landlord funds held in escrow, not yet withdrawable
    val availableBalanceKsh: Long = 0       // NEW — landlord funds settled and withdrawable
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
    val totalUnits: Int = 1,                // NEW — number of identical units this listing represents
    val unitsAvailable: Int = 1,            // NEW — units not currently RESERVED or taken
    val depositKsh: Long = 0,               // NEW — reservation deposit amount, in KSh
    @get:PropertyName("paused") @set:PropertyName("paused") var paused: Boolean = false, // NEW — landlord override, independent of unitsAvailable
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

// NEW — one unit-level reservation created when a student pays a deposit
data class Reservation(
    val id: String = "",
    val listingId: String = "",
    val listingTitle: String = "", // denormalized for display, same pattern as landlordName on Listing
    val landlordUid: String = "",
    val landlordPhone: String = "", // so the reservation card can show/call the landlord directly
    val studentUid: String = "",
    val studentName: String = "",
    val studentPhone: String = "", // NEW — so a landlord can see/call who reserved their unit
    val depositKsh: Long = 0,
    val status: ReservationStatus = ReservationStatus.RESERVED,
    val reservedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,           // set when CONFIRMED or REJECTED
    val rejectionReason: String = ""
)

// NEW — one entry in a landlord's wallet transaction history
data class WalletTransaction(
    val id: String = "",
    val landlordUid: String = "",
    val landlordName: String = "", // NEW — denormalized so admin's platform-wide view can show who each entry belongs to
    val listingId: String = "",
    val reservationId: String = "",
    val type: TransactionType = TransactionType.DEPOSIT_HELD,
    val amountKsh: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)