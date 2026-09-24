package com.korkutsoftware.doruhavadisadmin

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.korkutsoftware.doruhavadisadmin.login.LoginActivity
import com.korkutsoftware.doruhavadisadmin.pdf.DigitalNewspapersActivity

class DashboardActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mAuth: FirebaseAuth
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        
        mAuth = FirebaseAuth.getInstance()
        drawerLayout = findViewById(R.id.drawer_layout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val navigationView: NavigationView = findViewById(R.id.nav_view)

        // Define top-level destinations for the drawer and bottom nav
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_news,
                R.id.navigation_edit,
                R.id.navigation_columnist,
                R.id.navigation_tests,
                R.id.navigation_profile,
                R.id.navigation_site_status
            ), drawerLayout
        )

        // Setup Bottom Navigation
        bottomNav.setupWithNavController(navController)
        
        // Setup Navigation Drawer
        navigationView.setupWithNavController(navController)

        // Ensure bottom nav selection stays in sync when navigating manually
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (setOf(R.id.navigation_home, R.id.navigation_news, R.id.navigation_edit, R.id.navigation_columnist, R.id.navigation_tests).contains(destination.id)) {
                bottomNav.menu.findItem(destination.id)?.isChecked = true
            }
        }

        // Handle custom actions in Navigation Drawer (like Logout & Digital Newspapers)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_logout -> {
                    logout()
                    true
                }
                R.id.navigation_digital_newspapers -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    val intent = Intent(this, DigitalNewspapersActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> {
                    val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                    if (handled) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    handled
                }
            }
        }

        // Modern OnBackPressed handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun logout() {
        mAuth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
