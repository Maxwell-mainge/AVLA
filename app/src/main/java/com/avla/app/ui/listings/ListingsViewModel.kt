package com.avla.app.ui.listings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avla.app.data.model.Listing
import com.avla.app.data.model.ListingFilter
import com.avla.app.data.repository.FirebaseRepository
import com.avla.app.utils.UiState
import kotlinx.coroutines.launch

class ListingsViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    private val _listings = MutableLiveData<UiState<List<Listing>>>()
    val listings: LiveData<UiState<List<Listing>>> = _listings

    private val _myListings = MutableLiveData<UiState<List<Listing>>>()
    val myListings: LiveData<UiState<List<Listing>>> = _myListings

    private val _postState = MutableLiveData<UiState<String>>()
    val postState: LiveData<UiState<String>> = _postState

    // ─── Favorites ───────────────────────────────────────────
    // Set of favorited listing IDs for the current student, kept in memory
    // so any grid on screen can show the correct heart state instantly.
    private val _favoriteIds = MutableLiveData<Set<String>>(emptySet())
    val favoriteIds: LiveData<Set<String>> = _favoriteIds

    private val _favoriteListings = MutableLiveData<UiState<List<Listing>>>()
    val favoriteListings: LiveData<UiState<List<Listing>>> = _favoriteListings

    fun loadFavoriteIds(uid: String) {
        viewModelScope.launch {
            try {
                val user = repo.getUserProfile(uid)
                _favoriteIds.value = user?.favoriteListingIds?.toSet() ?: emptySet()
            } catch (_: Exception) {
                // Leave whatever we had before on failure
            }
        }
    }

    fun toggleFavorite(uid: String, listingId: String) {
        val currentlyFavorited = _favoriteIds.value?.contains(listingId) == true
        // Optimistic local update so the heart flips instantly
        _favoriteIds.value = if (currentlyFavorited) {
            (_favoriteIds.value ?: emptySet()) - listingId
        } else {
            (_favoriteIds.value ?: emptySet()) + listingId
        }

        viewModelScope.launch {
            try {
                repo.toggleFavorite(uid, listingId, currentlyFavorited)
            } catch (_: Exception) {
                // Revert on failure
                _favoriteIds.value = if (currentlyFavorited) {
                    (_favoriteIds.value ?: emptySet()) + listingId
                } else {
                    (_favoriteIds.value ?: emptySet()) - listingId
                }
            }
        }
    }

    fun loadFavoriteListings(uid: String) {
        _favoriteListings.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.fetchFavoriteListings(uid)
                _favoriteListings.value = UiState.Success(result)
                _favoriteIds.value = result.map { it.id }.toSet()
            } catch (e: Exception) {
                _favoriteListings.value = UiState.Error(e.message ?: "Failed to load favorites")
            }
        }
    }
    // ─────────────────────────────────────────────────────────

    fun loadListingsByProximity(studentCampus: String) {
        _listings.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.fetchListingsSortedByProximity(studentCampus)
                _listings.value = UiState.Success(result)
            } catch (e: Exception) {
                _listings.value = UiState.Error(e.message ?: "Failed to load listings")
            }
        }
    }

    fun searchListings(filter: ListingFilter, studentCampus: String = "") {
        _listings.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.fetchFilteredListings(filter, studentCampus)
                _listings.value = UiState.Success(result)
            } catch (e: Exception) {
                _listings.value = UiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun loadMyListings(landlordUid: String) {
        _myListings.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repo.fetchMyListings(landlordUid)
                _myListings.value = UiState.Success(result)
            } catch (e: Exception) {
                _myListings.value = UiState.Error(e.message ?: "Failed to load your listings")
            }
        }
    }

    fun postListing(listing: Listing) {
        _postState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val id = repo.postListing(listing)
                _postState.value = UiState.Success(id)
            } catch (e: Exception) {
                _postState.value = UiState.Error(e.message ?: "Failed to post listing")
            }
        }
    }

    fun deleteListing(listingId: String, landlordUid: String) {
        viewModelScope.launch {
            try { repo.deleteListing(listingId); loadMyListings(landlordUid) }
            catch (_: Exception) {}
        }
    }
}