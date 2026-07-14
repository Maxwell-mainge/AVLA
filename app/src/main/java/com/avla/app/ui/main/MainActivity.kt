package com.avla.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.avla.app.R
import com.avla.app.data.model.UserRole
import com.avla.app.databinding.ActivityMainBinding
import com.avla.app.utils.DataSeeder
import kotlinx.coroutines.launch

/**
 * Bottom-nav shell for student and landlord. Logout now lives only on the
 * Profile screen (both role Profile fragments already have a logout button)
 * rather than as a nav item — bottom nav tabs are meant for frequent
 * switching between top-level destinations, not one-off actions.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val roleStr    = intent.getStringExtra("USER_ROLE") ?: UserRole.STUDENT.name
        val userRole   = UserRole.valueOf(roleStr)
        val userCampus = intent.getStringExtra("USER_CAMPUS") ?: ""

        // Seed mock data in debug builds — no BuildConfig needed
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            lifecycleScope.launch {
                try { DataSeeder.seedIfNeeded() } catch (_: Exception) {}
            }
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        val inflater = navController.navInflater
        val graph = when (userRole) {
            UserRole.STUDENT  -> inflater.inflate(R.navigation.student_nav_graph)
            UserRole.LANDLORD -> inflater.inflate(R.navigation.landlord_nav_graph)
            else              -> inflater.inflate(R.navigation.student_nav_graph)
        }

        if (userRole == UserRole.STUDENT && userCampus.isNotBlank()) {
            val bundle = Bundle().apply { putString("campus", userCampus) }
            navController.setGraph(graph, bundle)
        } else {
            navController.graph = graph
        }

        val topLevel = when (userRole) {
            UserRole.STUDENT  -> setOf(R.id.homeFragment, R.id.searchFragment, R.id.favoritesFragment, R.id.profileFragment)
            UserRole.LANDLORD -> setOf(R.id.myListingsFragment, R.id.postListingFragment, R.id.profileFragment)
            else              -> setOf(R.id.homeFragment)
        }

        // No drawer now, so AppBarConfiguration just needs the top-level set —
        // non-top-level screens (like listing detail) automatically get a
        // back arrow instead of a hamburger icon.
        appBarConfig = AppBarConfiguration(topLevel)
        setupActionBarWithNavController(navController, appBarConfig)

        val menuRes = if (userRole == UserRole.STUDENT) R.menu.bottom_nav_student else R.menu.bottom_nav_landlord
        binding.bottomNav.menu.clear()
        binding.bottomNav.inflateMenu(menuRes)
        binding.bottomNav.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp() =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()
}