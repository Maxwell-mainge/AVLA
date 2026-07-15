package com.avla.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avla.app.data.model.ActivityLog
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.Listing
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.model.VerificationStatus
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.utils.UiState
import kotlinx.coroutines.launch

class AdminViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    private val _pendingLandlords = MutableLiveData<UiState<List<AppUser>>>()
    val pendingLandlords: LiveData<UiState<List<AppUser>>> = _pendingLandlords

    private val _allListings = MutableLiveData<UiState<List<Listing>>>()
    val allListings: LiveData<UiState<List<Listing>>> = _allListings

    // NEW — landlords list, used to populate the "filter by landlord"
    // autocomplete on the admin listings screen.
    private val _landlords = MutableLiveData<UiState<List<AppUser>>>()
    val landlords: LiveData<UiState<List<AppUser>>> = _landlords

    private val _allUsers = MutableLiveData<UiState<List<AppUser>>>()
    val allUsers: LiveData<UiState<List<AppUser>>> = _allUsers

    private val _recentActivity = MutableLiveData<UiState<List<ActivityLog>>>()
    val recentActivity: LiveData<UiState<List<ActivityLog>>> = _recentActivity

    private val _actionState = MutableLiveData<UiState<String>>()
    val actionState: LiveData<UiState<String>> = _actionState

    fun loadPendingLandlords() {
        _pendingLandlords.value = UiState.Loading
        viewModelScope.launch {
            try { _pendingLandlords.value = UiState.Success(repo.fetchPendingLandlords()) }
            catch (e: Exception) { _pendingLandlords.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun loadAllListings() {
        _allListings.value = UiState.Loading
        viewModelScope.launch {
            try { _allListings.value = UiState.Success(repo.fetchAllListings()) }
            catch (e: Exception) { _allListings.value = UiState.Error(e.message ?: "Error") }
        }
    }

    /**
     * Applies the admin filter panel (landlord, property type, price range,
     * location) and pushes the result into the same allListings LiveData the
     * fragment already observes — no separate observer needed.
     */
    fun applyListingFilter(filter: ListingFilter) {
        _allListings.value = UiState.Loading
        viewModelScope.launch {
            try { _allListings.value = UiState.Success(repo.fetchAllListingsFiltered(filter)) }
            catch (e: Exception) { _allListings.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun loadLandlords() {
        _landlords.value = UiState.Loading
        viewModelScope.launch {
            try { _landlords.value = UiState.Success(repo.fetchLandlords()) }
            catch (e: Exception) { _landlords.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun loadAllUsers() {
        _allUsers.value = UiState.Loading
        viewModelScope.launch {
            try { _allUsers.value = UiState.Success(repo.fetchAllUsers()) }
            catch (e: Exception) { _allUsers.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun loadRecentActivity() {
        _recentActivity.value = UiState.Loading
        viewModelScope.launch {
            try { _recentActivity.value = UiState.Success(repo.fetchRecentActivity()) }
            catch (e: Exception) { _recentActivity.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun approveLandlord(uid: String) {
        viewModelScope.launch {
            try {
                repo.updateVerificationStatus(uid, VerificationStatus.VERIFIED)
                _actionState.value = UiState.Success("Landlord approved")
                loadPendingLandlords()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun rejectLandlord(uid: String, reason: String) {
        viewModelScope.launch {
            try {
                repo.updateVerificationStatus(uid, VerificationStatus.REJECTED, reason)
                _actionState.value = UiState.Success("Landlord rejected")
                loadPendingLandlords()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun suspendUser(uid: String, reason: String) {
        viewModelScope.launch {
            try {
                repo.suspendUser(uid, reason)
                _actionState.value = UiState.Success("User suspended")
                loadAllUsers()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun unsuspendUser(uid: String) {
        viewModelScope.launch {
            try {
                repo.unsuspendUser(uid)
                _actionState.value = UiState.Success("User unsuspended")
                loadAllUsers()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun flagStudentId(uid: String, reason: String) {
        viewModelScope.launch {
            try {
                repo.flagStudentId(uid, reason)
                _actionState.value = UiState.Success("Student ID flagged")
                loadAllUsers()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun unflagStudentId(uid: String) {
        viewModelScope.launch {
            try {
                repo.unflagStudentId(uid)
                _actionState.value = UiState.Success("Student ID flag cleared")
                loadAllUsers()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    /**
     * NEW — admin's decision on a student's resubmitted school ID.
     * approved=true clears the flag entirely; approved=false keeps them
     * flagged with a new reason and routes them back to the resubmit form
     * (idResubmitted resets to false) next time they log in.
     */
    fun reviewResubmittedStudentId(uid: String, approved: Boolean, newReason: String = "") {
        viewModelScope.launch {
            try {
                repo.reviewResubmittedStudentId(uid, approved, newReason)
                _actionState.value = UiState.Success(
                    if (approved) "Resubmission approved" else "Resubmission rejected"
                )
                loadAllUsers()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }

    fun deleteListing(listingId: String) {
        viewModelScope.launch {
            try {
                repo.deleteListing(listingId)
                _actionState.value = UiState.Success("Listing removed")
                loadAllListings()
            } catch (e: Exception) { _actionState.value = UiState.Error(e.message ?: "Error") }
        }
    }
}