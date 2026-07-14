package com.avla.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.UserRole
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.utils.UiState
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    private val _authState = MutableLiveData<UiState<AppUser>>()
    val authState: LiveData<UiState<AppUser>> = _authState

    // Separate from authState so loading an existing profile for the resubmit
    // form (pre-fill) never collides with the login/register success handling.
    private val _profileState = MutableLiveData<UiState<AppUser>>()
    val profileState: LiveData<UiState<AppUser>> = _profileState

    // Separate from authState so a successful resubmit doesn't accidentally
    // trigger the "navigate to verifyEmailFragment" logic meant for fresh registration.
    private val _resubmitState = MutableLiveData<UiState<Unit>>()
    val resubmitState: LiveData<UiState<Unit>> = _resubmitState

    fun registerStudent(
        fullName: String,
        email: String,
        phone: String,
        nationalId: String,
        campus: String,
        studentIdDocLink: String,
        password: String
    ) {
        _authState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.registerWithEmail(email, password)
                val uid = result.user?.uid ?: error("UID null")
                val user = AppUser(
                    uid              = uid,
                    fullName         = fullName,
                    email            = email,
                    phone            = phone,
                    nationalId       = nationalId,
                    role             = UserRole.STUDENT,
                    campus           = campus,
                    studentIdDocLink = studentIdDocLink
                )
                repo.saveUserProfile(user)
                _authState.value = UiState.Success(user)
            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun registerLandlord(
        fullName: String,
        email: String,
        phone: String,
        nationalId: String,
        operatingLocation: String,
        password: String,
        nationalIdDocLink: String,
        propertyDocLink: String,
        propertyDocType: String
    ) {
        _authState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.registerWithEmail(email, password)
                val uid = result.user?.uid ?: error("UID null")
                val user = AppUser(
                    uid               = uid,
                    fullName          = fullName,
                    email             = email,
                    phone             = phone,
                    nationalId        = nationalId,
                    role              = UserRole.LANDLORD,
                    operatingLocation = operatingLocation,
                    nationalIdDocLink = nationalIdDocLink,
                    propertyDocLink   = propertyDocLink,
                    propertyDocType   = propertyDocType
                )
                repo.saveUserProfile(user)
                _authState.value = UiState.Success(user)
            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    /**
     * Loads the currently signed-in user's own profile — used to pre-fill
     * the resubmit form for a landlord stuck in pending/rejected status.
     */
    fun loadCurrentUserProfile() {
        _profileState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = repo.currentUser?.uid ?: error("Not signed in")
                val user = repo.getUserProfile(uid) ?: error("Profile not found")
                _profileState.value = UiState.Success(user)
            } catch (e: Exception) {
                _profileState.value = UiState.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    /**
     * Resubmits documents for an existing landlord account (already signed in)
     * instead of trying to create a brand new one.
     */
    fun resubmitLandlordDocuments(
        operatingLocation: String,
        nationalIdDocLink: String,
        propertyDocLink: String,
        propertyDocType: String
    ) {
        _resubmitState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val uid = repo.currentUser?.uid ?: error("Not signed in")
                repo.resubmitLandlordDocuments(
                    uid, operatingLocation, nationalIdDocLink, propertyDocLink, propertyDocType
                )
                _resubmitState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _resubmitState.value = UiState.Error(e.message ?: "Resubmit failed")
            }
        }
    }

    fun login(email: String, password: String) {
        _authState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repo.loginWithEmail(email, password)
                val uid  = repo.currentUser?.uid ?: error("UID null after login")
                val user = repo.getUserProfile(uid) ?: error("Profile not found")
                _authState.value = UiState.Success(user)
            } catch (e: Exception) {
                _authState.value = UiState.Error(e.message ?: "Login failed")
            }
        }
    }

    // Separate from authState so a password reset request never triggers
    // login-success navigation logic.
    private val _resetPasswordState = MutableLiveData<UiState<String>>()
    val resetPasswordState: LiveData<UiState<String>> = _resetPasswordState

    fun sendPasswordReset(email: String) {
        _resetPasswordState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repo.sendPasswordResetEmail(email)
                _resetPasswordState.value = UiState.Success("Password reset email sent to $email")
            } catch (e: Exception) {
                _resetPasswordState.value = UiState.Error(e.message ?: "Failed to send reset email")
            }
        }
    }

    fun logout() = repo.logout()
}