package com.avla.app.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.avla.app.R
import com.avla.app.databinding.ActivityAuthBinding

/**
 * Single-activity shell for the auth flow.
 * Navigation graph: auth_nav_graph.xml
 *   └─ LoginFragment  (start destination)
 *       ├─► RegisterFragment
 *       └─► VerifyEmailFragment
 *
 * Also handles being relaunched from LoginFragment with either:
 *   - SHOW_PENDING=true — a landlord whose verification is still pending
 *     or was rejected, routes to landlordPendingFragment.
 *   - SHOW_ID_FLAGGED=true — a student whose school ID was flagged on
 *     spot-check, routes to studentIdResubmitFragment. Unlike SHOW_PENDING,
 *     this never means the student is blocked from the app — only that
 *     they need to resubmit before continuing. ID_RESUBMITTED tells that
 *     screen whether the student already resubmitted and is just waiting
 *     on an admin recheck, vs. still needing to fill out the form.
 */
class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

        if (intent.getBooleanExtra("SHOW_PENDING", false)) {
            val bundle = Bundle().apply {
                putBoolean("IS_REJECTED", intent.getBooleanExtra("IS_REJECTED", false))
                putString("REJECTION_REASON", intent.getStringExtra("REJECTION_REASON") ?: "")
            }
            navHost?.navController?.navigate(R.id.landlordPendingFragment, bundle)
        } else if (intent.getBooleanExtra("SHOW_ID_FLAGGED", false)) {
            val bundle = Bundle().apply {
                putString("ID_FLAG_REASON", intent.getStringExtra("ID_FLAG_REASON") ?: "")
                putBoolean("ID_RESUBMITTED", intent.getBooleanExtra("ID_RESUBMITTED", false))
            }
            navHost?.navController?.navigate(R.id.studentIdResubmitFragment, bundle)
        }
    }
}