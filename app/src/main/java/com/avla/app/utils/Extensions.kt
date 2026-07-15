package com.avla.app.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) =
    Snackbar.make(this, message, duration).show()

fun validateEmail(email: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

fun validatePassword(password: String): Boolean = password.length >= 6

/** Accepts 07XXXXXXXX or 01XXXXXXXX */
fun validateKenyanPhone(phone: String): Boolean =
    Regex("^(07|01)\\d{8}$").matches(phone)

/** Kenyan national ID: 7–8 digits */
fun validateNationalId(id: String): Boolean =
    Regex("^\\d{7,8}$").matches(id)
