package com.avla.app.data.repository

import com.avla.app.data.model.ActivityLog
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.Listing
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private fun db() = FirebaseFirestore.getInstance()

    val currentUser get() = auth.currentUser

    // ═══════════════════════════════════════════════════════
    // AUTH
    // ═══════════════════════════════════════════════════════

    suspend fun registerWithEmail(email: String, password: String) =
        auth.createUserWithEmailAndPassword(email, password).await()

    suspend fun sendEmailVerification() =
        auth.currentUser?.sendEmailVerification()?.await()

    suspend fun loginWithEmail(email: String, password: String) =
        auth.signInWithEmailAndPassword(email, password).await()

    suspend fun sendPasswordResetEmail(email: String) =
        auth.sendPasswordResetEmail(email).await()

    /**
     * Changes the current user's password. Firebase requires a recent
     * sign-in for this kind of sensitive operation, so we reauthenticate
     * with their current password first.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser ?: error("Not signed in")
        val email = user.email ?: error("No email on this account")
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
        user.reauthenticate(credential).await()
        user.updatePassword(newPassword).await()
    }

    fun logout() = auth.signOut()

    // ═══════════════════════════════════════════════════════
    // USERS
    // ═══════════════════════════════════════════════════════

    suspend fun saveUserProfile(user: AppUser) {
        db().collection("users").document(user.uid).set(user).await()

        // NOTE: if this function is also called for profile *edits* (not just
        // first-time registration), this will log an activity entry on every
        // edit too. If that's the case, add an `isNewAccount: Boolean = true`
        // parameter and only log when it's true.
        if (user.role == UserRole.LANDLORD) {
            logActivity(
                type = "landlord_registered",
                title = "New landlord registration",
                subtitle = "${user.fullName} registered as a landlord"
            )
        } else {
            logActivity(
                type = "user_created",
                title = "New user account created",
                subtitle = "${user.fullName} joined the platform"
            )
        }
    }

    suspend fun getUserProfile(uid: String): AppUser? =
        db().collection("users").document(uid).get().await()
            .toObject(AppUser::class.java)

    suspend fun updateVerificationStatus(
        landlordUid: String,
        status: VerificationStatus,
        rejectionReason: String = ""
    ) {
        val updates = mutableMapOf<String, Any>(
            "verificationStatus" to status.name,
            "rejectionReason"    to rejectionReason
        )
        when (status) {
            VerificationStatus.VERIFIED -> updates["verifiedAt"] = System.currentTimeMillis()
            VerificationStatus.REJECTED -> updates["rejectedAt"] = System.currentTimeMillis()
            else -> Unit
        }

        db().collection("users").document(landlordUid).update(updates).await()

        if (status == VerificationStatus.VERIFIED) {
            val landlord = getUserProfile(landlordUid)
            logActivity(
                type = "landlord_verified",
                title = "Landlord verified successfully",
                subtitle = "${landlord?.fullName ?: "A landlord"} has been verified"
            )
        }
    }

    suspend fun suspendUser(uid: String, reason: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "suspended"        to true,
                "suspensionReason" to reason
            )
        ).await()
    }

    suspend fun unsuspendUser(uid: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "suspended"        to false,
                "suspensionReason" to ""
            )
        ).await()
    }

    /**
     * Flags a student's school ID as invalid on spot-check. Distinct from
     * suspendUser — a flagged student can still log in and use the app, they
     * just get routed through a resubmit screen first. Suspension is reserved
     * for actual misconduct and is checked independently at login.
     */
    suspend fun flagStudentId(uid: String, reason: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "idFlagged"     to true,
                "idFlagReason"  to reason,
                "idResubmitted" to false
            )
        ).await()
    }

    suspend fun unflagStudentId(uid: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "idFlagged"     to false,
                "idFlagReason"  to "",
                "idResubmitted" to false
            )
        ).await()
    }

    /**
     * For a student whose school ID was flagged — records the new doc link and
     * marks it as resubmitted, but does NOT clear the flag. The student stays
     * routed to the "under review" state until an admin explicitly approves or
     * rejects the resubmission via reviewResubmittedStudentId(). This mirrors
     * how landlord resubmission goes back to PENDING instead of straight to
     * VERIFIED.
     */
    suspend fun resubmitStudentId(uid: String, newStudentIdDocLink: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "studentIdDocLink" to newStudentIdDocLink,
                "idResubmitted"    to true
            )
        ).await()
    }

    /**
     * Admin's decision after reviewing a student's resubmitted school ID.
     * approved = true  -> clears the flag entirely; student regains normal,
     *                      unflagged access next time idFlagged is checked.
     * approved = false -> flag stays, idResubmitted resets to false so the
     *                      student is routed back to the resubmit form, and
     *                      idFlagReason is updated with the new reason.
     */
    suspend fun reviewResubmittedStudentId(uid: String, approved: Boolean, newReason: String = "") {
        val updates = if (approved) {
            mapOf(
                "idFlagged"     to false,
                "idFlagReason"  to "",
                "idResubmitted" to false
            )
        } else {
            mapOf(
                "idFlagged"     to true,
                "idFlagReason"  to newReason,
                "idResubmitted" to false
            )
        }
        db().collection("users").document(uid).update(updates).await()

        if (approved) {
            val student = getUserProfile(uid)
            logActivity(
                type = "student_id_reverified",
                title = "Student ID re-verified",
                subtitle = "${student?.fullName ?: "A student"}'s resubmitted ID was approved"
            )
        }
    }

    /**
     * For a landlord whose account already exists (pending or rejected) —
     * updates their existing profile with resubmitted documents/location and
     * resets status back to PENDING, instead of trying to create a brand new
     * Firebase Auth account (which would fail with a duplicate-email error).
     */
    suspend fun resubmitLandlordDocuments(
        uid: String,
        operatingLocation: String,
        nationalIdDocLink: String,
        propertyDocLink: String,
        propertyDocType: String
    ) {
        db().collection("users").document(uid).update(
            mapOf(
                "operatingLocation"  to operatingLocation,
                "nationalIdDocLink"  to nationalIdDocLink,
                "propertyDocLink"    to propertyDocLink,
                "propertyDocType"    to propertyDocType,
                "verificationStatus" to VerificationStatus.PENDING.name,
                "rejectionReason"    to ""
            )
        ).await()
    }

    suspend fun fetchPendingLandlords(): List<AppUser> =
        db().collection("users")
            .whereEqualTo("role", UserRole.LANDLORD.name)
            .whereEqualTo("verificationStatus", VerificationStatus.PENDING.name)
            .get().await()
            .toObjects(AppUser::class.java)

    suspend fun fetchAllUsers(): List<AppUser> =
        db().collection("users").get().await().toObjects(AppUser::class.java)

    suspend fun fetchLandlords(): List<AppUser> =
        db().collection("users")
            .whereEqualTo("role", UserRole.LANDLORD.name)
            .get().await()
            .toObjects(AppUser::class.java)

    // ═══════════════════════════════════════════════════════
    // ACTIVITY LOG
    // Lightweight feed for the admin dashboard's "Recent Activity" card.
    // Call logActivity(...) from wherever the relevant action happens
    // (landlord registration, listing creation, verification, etc).
    // ═══════════════════════════════════════════════════════

    suspend fun logActivity(type: String, title: String, subtitle: String = "") {
        val entry = mapOf(
            "type"      to type,
            "title"     to title,
            "subtitle"  to subtitle,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db().collection("activity").add(entry).await()
    }

    suspend fun fetchRecentActivity(limit: Long = 5): List<ActivityLog> =
        db().collection("activity")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .documents.map { doc ->
                ActivityLog(
                    id = doc.id,
                    type = doc.getString("type") ?: "",
                    title = doc.getString("title") ?: "",
                    subtitle = doc.getString("subtitle") ?: "",
                    timestamp = doc.getTimestamp("timestamp")
                )
            }

    // ═══════════════════════════════════════════════════════
    // FAVORITES
    // Stored as a single array field (favoriteListingIds) on the student's
    // existing user document — no separate collection needed.
    // ═══════════════════════════════════════════════════════

    suspend fun addFavorite(uid: String, listingId: String) {
        db().collection("users").document(uid)
            .update("favoriteListingIds", FieldValue.arrayUnion(listingId))
            .await()
        db().collection("listingStats").document(listingId)
            .set(mapOf("favoriteCount" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun removeFavorite(uid: String, listingId: String) {
        db().collection("users").document(uid)
            .update("favoriteListingIds", FieldValue.arrayRemove(listingId))
            .await()
        db().collection("listingStats").document(listingId)
            .set(mapOf("favoriteCount" to FieldValue.increment(-1)), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /**
     * Toggles favorite state for a listing. Returns the new state
     * (true = now favorited, false = now un-favorited).
     */
    suspend fun toggleFavorite(uid: String, listingId: String, currentlyFavorited: Boolean): Boolean {
        return if (currentlyFavorited) {
            removeFavorite(uid, listingId)
            false
        } else {
            addFavorite(uid, listingId)
            true
        }
    }

    suspend fun fetchFavoriteListings(uid: String): List<Listing> {
        val user = getUserProfile(uid) ?: return emptyList()
        if (user.favoriteListingIds.isEmpty()) return emptyList()

        return fetchAllRaw()
            .map { (listing, _) -> listing }
            .filter { it.id in user.favoriteListingIds }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Counts how many students currently have this listing favorited.
     * Reads from a dedicated listingStats/{listingId} counter doc rather than
     * querying the users collection directly — the users collection is locked
     * down per-user by security rules, so cross-user array-contains queries
     * silently return only the caller's own document.
     */
    suspend fun getFavoriteCount(listingId: String): Int {
        val snapshot = db().collection("listingStats").document(listingId).get().await()
        return (snapshot.getLong("favoriteCount") ?: 0L).toInt().coerceAtLeast(0)
    }

    /**
     * Fetches favorite counts for multiple listings at once (e.g. for a
     * landlord's "My Listings" grid). Runs the reads concurrently.
     */
    suspend fun getFavoriteCounts(listingIds: List<String>): Map<String, Int> {
        if (listingIds.isEmpty()) return emptyMap()
        return coroutineScope {
            val ids = listingIds
            val counts = ids.map { id -> async { getFavoriteCount(id) } }.awaitAll()
            ids.zip(counts).toMap()
        }
    }

    // ═══════════════════════════════════════════════════════
    // LISTINGS
    // ═══════════════════════════════════════════════════════

    suspend fun postListing(listing: Listing): String {
        val ref    = db().collection("listings").document()
        val withId = listing.copy(id = ref.id)
        ref.set(withId).await()

        logActivity(
            type = "listing_published",
            title = "New listing published",
            subtitle = withId.location
        )

        return ref.id
    }

    suspend fun deleteListing(listingId: String) {
        db().collection("listings").document(listingId).delete().await()
    }

    /**
     * Fetch all listings and determine availability by checking both
     * "available" and "isAvailable" field names from raw Firestore data.
     * This handles old seeded docs and new app-posted docs.
     */
    private suspend fun fetchAllRaw(): List<Pair<Listing, Boolean>> {
        val snapshot = db().collection("listings").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val listing = doc.toObject(Listing::class.java) ?: return@mapNotNull null
            // Check both possible field names for availability
            val isAvailableField = doc.getBoolean("isAvailable") ?: true
            val availableField   = doc.getBoolean("available")   ?: true
            val isAvailable      = isAvailableField && availableField
            Pair(listing, isAvailable)
        }
    }

    suspend fun fetchListingsSortedByProximity(studentCampus: String): List<Listing> {
        return fetchAllRaw()
            .filter { (_, available) -> available }
            .map { (listing, _) -> listing }
            .sortedWith(compareBy(
                { if (it.nearCampus.equals(studentCampus, ignoreCase = true)) 0 else 1 },
                { it.distanceKm },
                { -it.createdAt }
            ))
    }

    suspend fun fetchMyListings(landlordUid: String): List<Listing> {
        return fetchAllRaw()
            .map { (listing, _) -> listing }
            .filter { it.landlordUid == landlordUid }
            .sortedByDescending { it.createdAt }
    }

    suspend fun fetchFilteredListings(
        filter: ListingFilter,
        studentCampus: String = ""
    ): List<Listing> {
        return fetchAllRaw()
            .filter { (_, available) -> available }
            .map { (listing, _) -> listing }
            .filter { it.priceKsh >= filter.minPriceKsh && it.priceKsh <= filter.maxPriceKsh }
            .filter { filter.propertyType == null || it.propertyType == filter.propertyType }
            .filter { filter.location.isBlank() || it.location.contains(filter.location, ignoreCase = true) }
            .sortedWith(compareBy(
                { if (it.nearCampus.equals(studentCampus, ignoreCase = true)) 0 else 1 },
                { it.distanceKm }
            ))
    }

    suspend fun fetchAllListings(): List<Listing> =
        fetchAllRaw()
            .map { (listing, _) -> listing }
            .sortedByDescending { it.createdAt }

    /**
     * Admin's version of fetchFilteredListings — filters by price/type/location
     * like the student-facing filter, but across ALL listings regardless of
     * availability (admin needs to see hidden/occupied listings too), and
     * without campus-proximity sorting since that's not relevant here.
     */
    suspend fun fetchAllListingsFiltered(filter: ListingFilter): List<Listing> {
        return fetchAllRaw()
            .map { (listing, _) -> listing }
            .filter { it.priceKsh >= filter.minPriceKsh && it.priceKsh <= filter.maxPriceKsh }
            .filter { filter.propertyType == null || it.propertyType == filter.propertyType }
            .filter { filter.location.isBlank() || it.location.contains(filter.location, ignoreCase = true) }
            .filter { filter.landlordUid.isNullOrBlank() || it.landlordUid == filter.landlordUid }
            .sortedByDescending { it.createdAt }
    }

    // ═══════════════════════════════════════════════════════
    // STORAGE
    // ═══════════════════════════════════════════════════════

    suspend fun uploadListingImage(
        listingId: String,
        fileName: String,
        imageBytes: ByteArray
    ): String {
        val ref = FirebaseStorage.getInstance()
            .reference.child("listings/$listingId/$fileName.jpg")
        ref.putBytes(imageBytes).await()
        return ref.downloadUrl.await().toString()
    }
}