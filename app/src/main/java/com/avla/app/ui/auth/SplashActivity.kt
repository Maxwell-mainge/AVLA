package com.avla.app.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.avla.app.data.model.AppUser
import com.avla.app.data.model.UserRole
import com.avla.app.data.model.VerificationStatus
import com.avla.app.ui.admin.AdminActivity
import com.avla.app.ui.main.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Shown on launch.
 * Native splash configuration holding user icon active until database routing resolves.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var isDataReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the native system splash screen handler BEFORE onCreate layout bindings
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // By design, AVLA never keeps users signed in between app launches —
        // every open requires a fresh login, regardless of role. Signing out
        // here means currentUser below is always null, which routes straight
        // to AuthActivity.
        FirebaseAuth.getInstance().signOut()

        // Keep the animated house logo locked on screen until our background worker data is ready
        splashScreen.setKeepOnScreenCondition { !isDataReady }

        lifecycleScope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                isDataReady = true
                startAuthActivity()
                return@launch
            }

            try {
                // Fetch profile data records to read the true user role
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(firebaseUser.uid)
                    .get()
                    .await()

                val userProfile = snapshot.toObject(AppUser::class.java)
                if (userProfile == null) {
                    isDataReady = true
                    startAuthActivity()
                    return@launch
                }

                // Precision role-based activity router layout targets matrix
                when {
                    userProfile.role == UserRole.ADMIN -> {
                        startActivity(Intent(this@SplashActivity, AdminActivity::class.java))
                    }
                    userProfile.role == UserRole.LANDLORD && userProfile.verificationStatus != VerificationStatus.VERIFIED -> {
                        val intent = Intent(this@SplashActivity, AuthActivity::class.java).apply {
                            putExtra("ROUTE_TO_PENDING", true)
                            putExtra("IS_REJECTED", userProfile.verificationStatus == VerificationStatus.REJECTED)
                            putExtra("REJECTION_REASON", userProfile.rejectionReason)
                        }
                        startActivity(intent)
                    }
                    else -> {
                        val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                            putExtra("USER_UID", userProfile.uid)
                            putExtra("USER_ROLE", userProfile.role.name)
                            putExtra("USER_CAMPUS", userProfile.campus)
                        }
                        startActivity(intent)
                    }
                }

                isDataReady = true // Dismisses the house icon splash cleanly
                finish()

            } catch (_: Exception) { // Renamed to underscore to completely remove the unused variable notice
                isDataReady = true
                startAuthActivity()
            }
        }
    }

    private fun startAuthActivity() {
        startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
        finish()
    }
}