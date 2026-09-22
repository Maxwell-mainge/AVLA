package com.avla.app.data.repository

import com.avla.app.data.model.ActivityLog
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.Listing
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.avla.app.data.model.Reservation
import com.avla.app.data.model.ReservationStatus
import com.avla.app.data.model.WalletTransaction
import com.avla.app.data.model.TransactionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.avla.app.utils.FcmService
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

    /**
     * NEW — saves a specific FCM token to a specific user's doc. Called from
     * AvlaFirebaseMessagingService.onNewToken() whenever the token is
     * created or rotated.
     */
    suspend fun updateFcmToken(uid: String, token: String) {
        db().collection("users").document(uid).update("fcmToken", token).await()
    }

    /**
     * NEW — fetches the current device's FCM token and saves it against
     * whoever is currently signed in. Called right after login/registration
     * succeeds, since onNewToken() only fires when the token is first
     * created or rotated — which may have happened before this user ever
     * signed in, so we'd otherwise miss it. Best-effort: failure here should
     * never block login/registration.
     */
    suspend fun saveFcmTokenForCurrentUser() {
        val uid = currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        updateFcmToken(uid, token)
    }

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

        val landlord = getUserProfile(landlordUid)

        if (status == VerificationStatus.VERIFIED) {
            logActivity(
                type = "landlord_verified",
                title = "Landlord verified successfully",
                subtitle = "${landlord?.fullName ?: "A landlord"} has been verified"
            )
        }

        // NEW — notify the landlord's device of the outcome
        when (status) {
            VerificationStatus.VERIFIED -> FcmService.sendNotification(
                landlord?.fcmToken.orEmpty(),
                "Account Verified",
                "Your landlord account has been verified! You can now post listings."
            )
            VerificationStatus.REJECTED -> FcmService.sendNotification(
                landlord?.fcmToken.orEmpty(),
                "Verification Rejected",
                "Your documents could not be verified. Reason: ${rejectionReason.ifBlank { "Not specified" }}"
            )
            else -> Unit
        }
    }

    suspend fun suspendUser(uid: String, reason: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "suspended"        to true,
                "suspensionReason" to reason
            )
        ).await()

        // NEW — notify the user's device
        val user = getUserProfile(uid)
        FcmService.sendNotification(
            user?.fcmToken.orEmpty(),
            "Account Suspended",
            "Your account has been suspended. Reason: ${reason.ifBlank { "Not specified" }}"
        )
    }

    suspend fun unsuspendUser(uid: String) {
        db().collection("users").document(uid).update(
            mapOf(
                "suspended"        to false,
                "suspensionReason" to ""
            )
        ).await()

        // NEW — notify the user's device
        val user = getUserProfile(uid)
        FcmService.sendNotification(
            user?.fcmToken.orEmpty(),
            "Account Reinstated",
            "Your account has been unsuspended. You can log in normally again."
        )
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

        // NEW — notify the student's device
        val student = getUserProfile(uid)
        FcmService.sendNotification(
            student?.fcmToken.orEmpty(),
            "School ID Flagged",
            "Your school ID needs to be resubmitted. Reason: ${reason.ifBlank { "Not specified" }}"
        )
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
     * -VERIFIED.
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

        val student = getUserProfile(uid)

        if (approved) {
            logActivity(
                type = "student_id_reverified",
                title = "Student ID re-verified",
                subtitle = "${student?.fullName ?: "A student"}'s resubmitted ID was approved"
            )
        }

        // NEW — notify the student's device of the outcome
        if (approved) {
            FcmService.sendNotification(
                student?.fcmToken.orEmpty(),
                "ID Verified",
                "Your resubmitted school ID has been approved."
            )
        } else {
            FcmService.sendNotification(
                student?.fcmToken.orEmpty(),
                "Resubmission Rejected",
                "Your resubmitted school ID needs another look. Reason: ${newReason.ifBlank { "Not specified" }}"
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
     * (true = now favorite, false = now un-favorite).
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
     *
     * NEW — a listing is now also hidden once its units run out
     * (unitsAvailable <= 0, handled automatically by reserveUnit()) or when
     * the landlord manually pauses it (paused == true) — two independent
     * reasons, same visible outcome, per the multi-unit/escrow design.
     */
    private suspend fun fetchAllRaw(): List<Pair<Listing, Boolean>> {
        val snapshot = db().collection("listings").get().await()
        return snapshot.documents.mapNotNull { doc ->
            val listing = doc.toObject(Listing::class.java) ?: return@mapNotNull null
            // Check both possible field names for availability
            val isAvailableField = doc.getBoolean("isAvailable") ?: true
            val availableField   = doc.getBoolean("available")   ?: true
            val isAvailable = isAvailableField && availableField &&
                    listing.unitsAvailable > 0 && !listing.paused
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
    // RESERVATIONS & ESCROW (NEW)
    // Split-ledger escrow: a reservation deposit moves a landlord's
    // pendingBalanceKsh up immediately, but only moves into their
    // withdrawable availableBalanceKsh once the student confirms, or once
    // the 48-hour window passes with no dispute (checked lazily — see
    // settleExpiredReservations below — no background job required).
    // Payment itself (STK push / B2C refund) is simulated: no real money
    // moves, only these Firestore-side ledger fields.
    // ═══════════════════════════════════════════════════════

    private val settlementWindowMs = 48L * 60 * 60 * 1000

    /**
     * Student pays a deposit on a listing with at least one unit free.
     * Atomically decrements unitsAvailable and creates the Reservation doc,
     * so two students racing for the last unit can't both succeed — the
     * loser's transaction sees unitsAvailable already at 0 and throws.
     */
    suspend fun reserveUnit(listingId: String, studentUid: String, studentName: String, studentPhone: String = ""): String {
        val reservationRef = db().collection("reservations").document()
        val listingRef = db().collection("listings").document(listingId)

        var landlordUid = ""
        var depositAmount = 0L

        db().runTransaction { txn ->
            val listingSnap = txn.get(listingRef)
            val listing = listingSnap.toObject(Listing::class.java)
                ?: throw IllegalStateException("Listing not found")

            if (listing.paused) throw IllegalStateException("This listing isn't taking reservations right now")
            if (listing.unitsAvailable <= 0) throw IllegalStateException("No units currently available")

            landlordUid = listing.landlordUid
            depositAmount = listing.depositKsh

            val landlordRef = db().collection("users").document(landlordUid)
            val landlordSnap = txn.get(landlordRef)
            val pending = landlordSnap.getLong("pendingBalanceKsh") ?: 0L

            txn.update(listingRef, "unitsAvailable", listing.unitsAvailable - 1)
            txn.update(landlordRef, "pendingBalanceKsh", pending + depositAmount)

            txn.set(reservationRef, Reservation(
                id = reservationRef.id,
                listingId = listingId,
                listingTitle = listing.title,
                landlordUid = landlordUid,
                landlordPhone = listing.landlordPhone,
                studentUid = studentUid,
                studentName = studentName,
                studentPhone = studentPhone,
                depositKsh = depositAmount,
                status = ReservationStatus.RESERVED,
                reservedAt = System.currentTimeMillis()
            ))

            val txnRef = db().collection("walletTransactions").document()
            txn.set(txnRef, WalletTransaction(
                id = txnRef.id,
                landlordUid = landlordUid,
                landlordName = landlordSnap.getString("fullName") ?: "",
                listingId = listingId,
                reservationId = reservationRef.id,
                type = TransactionType.DEPOSIT_HELD,
                amountKsh = depositAmount,
                createdAt = System.currentTimeMillis(),
                note = "Deposit held for $studentName's reservation (simulated M-Pesa STK push)"
            ))

            Unit
        }.await()

        logActivity(
            type = "unit_reserved",
            title = "Unit reserved",
            subtitle = "$studentName reserved a unit — KSh $depositAmount held"
        )

        val landlord = getUserProfile(landlordUid)
        FcmService.sendNotification(
            landlord?.fcmToken.orEmpty(),
            "New Reservation",
            "$studentName reserved a unit and paid a KSh $depositAmount deposit."
        )
        val student = getUserProfile(studentUid)
        FcmService.sendNotification(
            student?.fcmToken.orEmpty(),
            "Deposit Held",
            "Your KSh $depositAmount deposit is held. You have 48 hours to confirm or reject after viewing the unit."
        )

        return reservationRef.id
    }

    /**
     * Moves a reservation's deposit from the landlord's pendingBalanceKsh into
     * their withdrawable availableBalanceKsh. Called either when a student
     * explicitly confirms, or lazily by settleExpiredReservations() once the
     * 48-hour window has passed with no dispute. No-ops safely if the
     * reservation was already resolved (e.g. rejected) before this ran.
     */
    suspend fun confirmReservation(reservationId: String) {
        val reservationRef = db().collection("reservations").document(reservationId)

        var landlordUid = ""
        var depositAmount = 0L

        db().runTransaction { txn ->
            val snap = txn.get(reservationRef)
            val reservation = snap.toObject(Reservation::class.java)
                ?: throw IllegalStateException("Reservation not found")

            if (reservation.status == ReservationStatus.RESERVED) {
                landlordUid = reservation.landlordUid
                depositAmount = reservation.depositKsh

                val landlordRef = db().collection("users").document(landlordUid)
                val landlordSnap = txn.get(landlordRef)
                val pending = landlordSnap.getLong("pendingBalanceKsh") ?: 0L
                val available = landlordSnap.getLong("availableBalanceKsh") ?: 0L

                txn.update(landlordRef, mapOf(
                    "pendingBalanceKsh" to (pending - depositAmount).coerceAtLeast(0),
                    "availableBalanceKsh" to (available + depositAmount)
                ))
                txn.update(reservationRef, mapOf(
                    "status" to ReservationStatus.CONFIRMED.name,
                    "resolvedAt" to System.currentTimeMillis()
                ))

                val txnRef = db().collection("walletTransactions").document()
                txn.set(txnRef, WalletTransaction(
                    id = txnRef.id,
                    landlordUid = landlordUid,
                    landlordName = landlordSnap.getString("fullName") ?: "",
                    listingId = reservation.listingId,
                    reservationId = reservationId,
                    type = TransactionType.SETTLED,
                    amountKsh = depositAmount,
                    createdAt = System.currentTimeMillis(),
                    note = "Reservation confirmed — funds settled to available balance"
                ))
            }
            Unit
        }.await()

        if (landlordUid.isBlank()) return // already resolved before this call — nothing to notify

        logActivity(
            type = "reservation_settled",
            title = "Reservation settled",
            subtitle = "KSh $depositAmount settled to landlord's available balance"
        )

        val landlord = getUserProfile(landlordUid)
        FcmService.sendNotification(
            landlord?.fcmToken.orEmpty(),
            "Funds Settled",
            "KSh $depositAmount has settled to your available balance."
        )
    }

    /**
     * Student rejects a unit after visiting (or before the 48-hour window
     * runs out). Refunds the deposit (simulated M-Pesa B2C) by reversing the
     * landlord's pendingBalanceKsh, and gives the unit back to the pool.
     */
    suspend fun rejectReservation(reservationId: String, reason: String) {
        val reservationRef = db().collection("reservations").document(reservationId)

        var landlordUid = ""
        var studentUid = ""
        var depositAmount = 0L

        db().runTransaction { txn ->
            val snap = txn.get(reservationRef)
            val reservation = snap.toObject(Reservation::class.java)
                ?: throw IllegalStateException("Reservation not found")

            if (reservation.status == ReservationStatus.RESERVED) {
                landlordUid = reservation.landlordUid
                studentUid = reservation.studentUid
                depositAmount = reservation.depositKsh

                val landlordRef = db().collection("users").document(landlordUid)
                val landlordSnap = txn.get(landlordRef)
                val pending = landlordSnap.getLong("pendingBalanceKsh") ?: 0L

                val listingRef = db().collection("listings").document(reservation.listingId)
                val listingSnap = txn.get(listingRef)
                val currentUnits = listingSnap.getLong("unitsAvailable") ?: 0L

                txn.update(landlordRef, "pendingBalanceKsh", (pending - depositAmount).coerceAtLeast(0))
                txn.update(listingRef, "unitsAvailable", currentUnits + 1)
                txn.update(reservationRef, mapOf(
                    "status" to ReservationStatus.REJECTED.name,
                    "resolvedAt" to System.currentTimeMillis(),
                    "rejectionReason" to reason
                ))

                val txnRef = db().collection("walletTransactions").document()
                txn.set(txnRef, WalletTransaction(
                    id = txnRef.id,
                    landlordUid = landlordUid,
                    landlordName = landlordSnap.getString("fullName") ?: "",
                    listingId = reservation.listingId,
                    reservationId = reservationId,
                    type = TransactionType.REFUNDED,
                    amountKsh = depositAmount,
                    createdAt = System.currentTimeMillis(),
                    note = "Reservation rejected — deposit refunded (simulated M-Pesa B2C). Reason: ${reason.ifBlank { "Not specified" }}"
                ))
            }
            Unit
        }.await()

        if (landlordUid.isBlank()) return // already resolved before this call — nothing to notify

        logActivity(
            type = "reservation_rejected",
            title = "Reservation rejected",
            subtitle = "KSh $depositAmount refunded after rejection"
        )

        val landlord = getUserProfile(landlordUid)
        FcmService.sendNotification(
            landlord?.fcmToken.orEmpty(),
            "Reservation Rejected",
            "A reservation was rejected — the unit is available again."
        )
        val student = getUserProfile(studentUid)
        FcmService.sendNotification(
            student?.fcmToken.orEmpty(),
            "Deposit Refunded",
            "Your KSh $depositAmount deposit has been refunded."
        )
    }

    /**
     * Lazy settlement check — no Cloud Scheduler or background job needed.
     * Called from fetchReservationsForLandlord() on every read, so the
     * 48-hour window is honoured based on stored timestamps whether or not
     * anyone had the app open while it elapsed.
     */
    private suspend fun settleExpiredReservations(landlordUid: String) {
        val now = System.currentTimeMillis()
        val expired = db().collection("reservations")
            .whereEqualTo("landlordUid", landlordUid)
            .whereEqualTo("status", ReservationStatus.RESERVED.name)
            .get().await()
            .toObjects(Reservation::class.java)
            .filter { now - it.reservedAt >= settlementWindowMs }

        expired.forEach { confirmReservation(it.id) }
    }

    suspend fun fetchReservationsForStudent(uid: String): List<Reservation> =
        db().collection("reservations")
            .whereEqualTo("studentUid", uid)
            .get().await()
            .toObjects(Reservation::class.java)
            .sortedByDescending { it.reservedAt }

    /**
     * NEW — used by ListingDetailFragment to tell whether the CURRENT
     * student already has a pending reservation on THIS listing, so the
     * screen can show "Reservation Pending" instead of letting them pay for
     * a second unit on a listing they've already reserved from. Equality-
     * only compound query — no composite index needed.
     */
    suspend fun fetchActiveReservation(listingId: String, studentUid: String): Reservation? =
        db().collection("reservations")
            .whereEqualTo("listingId", listingId)
            .whereEqualTo("studentUid", studentUid)
            .whereEqualTo("status", ReservationStatus.RESERVED.name)
            .get().await()
            .toObjects(Reservation::class.java)
            .firstOrNull()

    suspend fun fetchReservationsForLandlord(uid: String): List<Reservation> {
        settleExpiredReservations(uid)
        return db().collection("reservations")
            .whereEqualTo("landlordUid", uid)
            .get().await()
            .toObjects(Reservation::class.java)
            .sortedByDescending { it.reservedAt }
    }

    suspend fun fetchWalletTransactions(landlordUid: String): List<WalletTransaction> =
        db().collection("walletTransactions")
            .whereEqualTo("landlordUid", landlordUid)
            .get().await()
            .toObjects(WalletTransaction::class.java)
            .sortedByDescending { it.createdAt }

    /**
     * NEW — platform-wide transaction feed for the Admin "Transactions" tab
     * (replaces the old bottom-nav Pending shortcut, which was redundant
     * with the Overview dashboard's Pending card). Unlike
     * fetchWalletTransactions(), this isn't scoped to one landlord.
     */
    suspend fun fetchAllWalletTransactions(): List<WalletTransaction> =
        db().collection("walletTransactions")
            .get().await()
            .toObjects(WalletTransaction::class.java)
            .sortedByDescending { it.createdAt }

    /**
     * Landlord withdraws from their settled, available balance. Simulated —
     * logs a WITHDRAWN transaction and decrements the balance, standing in
     * for a real M-Pesa B2C payout to the landlord's registered phone number.
     */
    suspend fun withdrawFunds(landlordUid: String, amountKsh: Long) {
        val landlordRef = db().collection("users").document(landlordUid)

        db().runTransaction { txn ->
            val snap = txn.get(landlordRef)
            val available = snap.getLong("availableBalanceKsh") ?: 0L
            if (amountKsh <= 0 || amountKsh > available) {
                throw IllegalStateException("Withdrawal amount exceeds available balance")
            }
            txn.update(landlordRef, "availableBalanceKsh", available - amountKsh)

            val txnRef = db().collection("walletTransactions").document()
            txn.set(txnRef, WalletTransaction(
                id = txnRef.id,
                landlordUid = landlordUid,
                landlordName = snap.getString("fullName") ?: "",
                listingId = "",
                reservationId = "",
                type = TransactionType.WITHDRAWN,
                amountKsh = amountKsh,
                createdAt = System.currentTimeMillis(),
                note = "Simulated M-Pesa B2C payout to registered phone number"
            ))
            Unit
        }.await()

        logActivity(
            type = "funds_withdrawn",
            title = "Landlord withdrawal",
            subtitle = "KSh $amountKsh withdrawn"
        )

        val landlord = getUserProfile(landlordUid)
        FcmService.sendNotification(
            landlord?.fcmToken.orEmpty(),
            "Withdrawal Complete",
            "Withdrawal of KSh $amountKsh to your M-Pesa is complete."
        )
    }

    /**
     * NEW — simulated flat fee a landlord pays to publish a listing, in
     * response to feedback that only students were paying anything on the
     * platform. Deliberately does NOT touch pendingBalanceKsh/
     * availableBalanceKsh — this money leaves the landlord toward the
     * platform, it isn't landlord earnings — so it's logged purely as a
     * PLATFORM_FEE transaction for visibility in the Admin's platform-wide
     * Transactions feed. Called from PostListingFragment right after
     * postListing() succeeds, once the simulated M-Pesa PIN step completes.
     */
    suspend fun payListingPostingFee(landlordUid: String, listingId: String, amountKsh: Long = 200) {
        val landlord = getUserProfile(landlordUid)

        val txnRef = db().collection("walletTransactions").document()
        txnRef.set(WalletTransaction(
            id = txnRef.id,
            landlordUid = landlordUid,
            landlordName = landlord?.fullName ?: "",
            listingId = listingId,
            reservationId = "",
            type = TransactionType.PLATFORM_FEE,
            amountKsh = amountKsh,
            createdAt = System.currentTimeMillis(),
            note = "Listing posting fee (simulated M-Pesa STK push)"
        )).await()

        logActivity(
            type = "posting_fee_paid",
            title = "Listing posting fee paid",
            subtitle = "${landlord?.fullName ?: "A landlord"} paid KSh $amountKsh to publish a listing"
        )
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