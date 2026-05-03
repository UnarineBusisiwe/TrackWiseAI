package com.example.trackwiseai

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.trackwiseai.DashboardFragment
import com.trackwiseai.AddExpenseFragment
import com.trackwiseai.ViewExpenseFragment
import com.trackwiseai.R
import com.trackwiseai.databinding.ActivityMainBinding
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.User
import com.trackwiseai.data.entities.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity - The main entry point for the TrackWise AI app
 *
 * This activity serves as the central hub for the entire application.
 * It handles:
 * - User authentication (login and account creation)
 * - Navigation drawer management
 * - Theme switching (Dark/Light mode)
 * - User session management
 * - Profile picture management
 * - Fragment navigation (Dashboard, Add Expense, View Expenses)
 *
 * @author TrackWise AI Team
 * @version 1.0
 */
class MainActivity : AppCompatActivity() {

    // ============================================================
    // PROPERTY DECLARATIONS
    // ============================================================

    /**
     * View Binding - Provides type-safe access to all views in activity_main.xml
     * This eliminates the need for findViewById() and reduces null pointer errors
     */
    private lateinit var binding: ActivityMainBinding

    /**
     * Room Database instance - Handles all local data storage operations
     * This is the main interface to our SQLite database
     */
    private lateinit var database: AppDatabase

    /**
     * SharedPreferences - Stores lightweight user session data and app settings
     * Used to remember login state, theme preference, and budget goals
     */
    private lateinit var sharedPrefs: SharedPreferences

    /**
     * Current logged-in user's ID
     * Value is -1 when no user is logged in
     */
    private var currentUserId: Long = -1

    /**
     * URI for user's profile picture
     * Stores the location of the image file in device storage
     */
    private var profileImageUri: Uri? = null

    // ============================================================
    // ACTIVITY RESULT LAUNCHERS
    // ============================================================

    /**
     * Image Picker Launcher
     * Opens the device's image gallery and handles the result
     */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = result.data?.data
            imageUri?.let { uri ->
                profileImageUri = uri
                sharedPrefs.edit().putString("profileImage", uri.toString()).apply()
                updateNavigationHeaderImage(uri)
            }
        }
    }

    // ============================================================
    // ACTIVITY LIFECYCLE METHODS
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // STEP 1: Apply saved theme BEFORE creating the activity
        val isDarkMode = getSharedPreferences("TrackWisePrefs", MODE_PRIVATE)
            .getBoolean("darkMode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // STEP 1.5: Enable Edge-to-Edge for Android 15 (SDK 35) support
        enableEdgeToEdge()

        // STEP 2: Call super.onCreate and set up the layout
        super.onCreate(savedInstanceState)

        // Inflate the layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets for Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // STEP 3: Initialize database and SharedPreferences
        database = AppDatabase.getInstance(this)
        sharedPrefs = getSharedPreferences("TrackWisePrefs", MODE_PRIVATE)

        // STEP 4: Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        // STEP 5: Set up navigation drawer and test user
        setupNavigationDrawer()
        createTestUserIfNeeded()

        // STEP 6: Check if user is already logged in
        currentUserId = sharedPrefs.getLong("userId", -1)

        if (currentUserId != -1L) {
            // User is logged in - show main app
            setupAppContent()
            loadUserProfile()
        } else {
            // No user logged in - show login screen
            showLoginScreen()
        }
    }

    // ============================================================
    // TEST USER CREATION (FOR DEBUGGING)
    // ============================================================

    /**
     * Creates a test user for debugging purposes
     * Test credentials: username = "testuser", password = "test123"
     */
    private fun createTestUserIfNeeded() {
        lifecycleScope.launch {
            try {
                val testUsername = "testuser"
                val exists = database.userDao().usernameExists(testUsername)

                if (!exists) {
                    Log.d("TrackWiseDebug", "Creating test user...")
                    val userId = database.userDao().insertUser(
                        User(
                            username = testUsername,
                            password = "test123",
                            monthlyMinGoal = 500.0,
                            monthlyMaxGoal = 3000.0
                        )
                    )

                    // Create default categories for test user
                    val defaultCategories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Healthcare")
                    defaultCategories.forEach { categoryName ->
                        database.categoryDao().insertCategory(
                            Category(
                                name = categoryName,
                                userId = userId
                            )
                        )
                    }
                    Log.d("TrackWiseDebug", "Test user created: testuser / test123")
                    Toast.makeText(this@MainActivity, "Test user created: testuser / test123", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("TrackWiseDebug", "Error creating test user: ${e.message}")
            }
        }
    }

    // ============================================================
    // NAVIGATION DRAWER SETUP
    // ============================================================

    /**
     * Sets up the navigation drawer (side menu)
     * Handles clicks on all menu items
     */
    private fun setupNavigationDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_add_expense -> {
                    loadFragment(AddExpenseFragment())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_view_expenses -> {
                    loadFragment(ViewExpenseFragment())
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_dark_mode -> {
                    setDarkMode()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_light_mode -> {
                    setLightMode()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_profile -> {
                    showProfileDialog()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_set_budget -> {
                    showSetBudgetDialog()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_about -> {
                    showAboutDialog()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_logout -> {
                    showLogoutDialog()
                    binding.drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }
    }

    // ============================================================
    // THEME MANAGEMENT
    // ============================================================

    /** Switches the app to Dark Mode */
    private fun setDarkMode() {
        sharedPrefs.edit().putBoolean("darkMode", true).apply()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        recreate()
    }

    /** Switches the app to Light Mode */
    private fun setLightMode() {
        sharedPrefs.edit().putBoolean("darkMode", false).apply()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        recreate()
    }

    // ============================================================
    // DIALOGS (About, Privacy Policy, Profile, Logout)
    // ============================================================

    /** Displays the About dialog with app information */
    private fun showAboutDialog() {
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }

        val aboutMessage = """
            TrackWise AI - Smart Budgeting App
            
            Version: $appVersion
            
            Features:
            • Track expenses with photos
            • Set monthly budget goals
            • AI-powered spending insights
            • Category-based reporting
            • Dark/Light mode support
            
            Developed for better financial management.
            
            © 2026 TrackWise AI. All rights reserved.
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("About TrackWise AI")
            .setMessage(aboutMessage)
            .setPositiveButton("OK", null)
            .setNeutralButton("Privacy Policy") { _, _ ->
                showPrivacyPolicy()
            }
            .show()
    }

    /** Displays the Privacy Policy dialog */
    private fun showPrivacyPolicy() {
        val privacyMessage = """
            Privacy Policy
            
            TrackWise AI respects your privacy. 
            
            • All data is stored locally on your device
            • No personal information is shared with third parties
            • Photos are stored only on your device
            • You can delete all data by uninstalling the app
            
            For questions, contact: support@trackwise.ai
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Privacy Policy")
            .setMessage(privacyMessage)
            .setPositiveButton("OK", null)
            .show()
    }

    /** Updates the profile image in the navigation drawer header */
    private fun updateNavigationHeaderImage(imageUri: Uri) {
        val headerView = binding.navigationView.getHeaderView(0)
        val profileImageView = headerView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivProfileImage)

        Glide.with(this)
            .load(imageUri)
            .circleCrop()
            .placeholder(R.drawable.ic_default_profile)
            .into(profileImageView)
    }

    /** Loads user profile from database and updates navigation header */
    private fun loadUserProfile() {
        lifecycleScope.launch {
            val user = database.userDao().getUser(currentUserId)
            user.collect { userData ->
                val headerView = binding.navigationView.getHeaderView(0)
                val tvUserName = headerView.findViewById<android.widget.TextView>(R.id.tvUserName)
                val tvUserEmail = headerView.findViewById<android.widget.TextView>(R.id.tvUserEmail)

                tvUserName.text = userData?.username ?: "User"
                tvUserEmail.text = "${userData?.username}@trackwise.com"
            }
        }

        val savedImageUri = sharedPrefs.getString("profileImage", null)
        savedImageUri?.let {
            updateNavigationHeaderImage(Uri.parse(it))
        }
    }

    /** Shows profile editing dialog */
    private fun showProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)

        val ivProfile = dialogView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivProfileDialog)
        val tvUserName = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogUserName)
        val btnChangePhoto = dialogView.findViewById<android.widget.Button>(R.id.btnChangePhoto)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

        profileImageUri?.let {
            Glide.with(this).load(it).circleCrop().into(ivProfile)
        }

        lifecycleScope.launch {
            database.userDao().getUser(currentUserId).collect { userData ->
                tvUserName.text = userData?.username ?: "User"
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()

        btnChangePhoto.setOnClickListener {
            openImagePicker()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
    }

    /** Opens image picker for profile picture */
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // ============================================================
    // TOOLBAR MENU
    // ============================================================

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            R.id.action_profile -> {
                showProfileDialog()
                true
            }
            R.id.action_set_budget -> {
                showSetBudgetDialog()
                true
            }
            R.id.action_logout -> {
                showLogoutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Shows confirmation dialog before logging out */
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Performs logout and returns to login screen */
    private fun performLogout() {
        sharedPrefs.edit().remove("userId").apply()
        currentUserId = -1
        showLoginScreen()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // AUTHENTICATION (Login & Account Creation)
    // ============================================================

    /** Displays the login screen */
    private fun showLoginScreen() {
        binding.loginLayout.visibility = android.view.View.VISIBLE
        binding.appContent.visibility = android.view.View.GONE

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim().lowercase()
            val password = binding.etPassword.text.toString()

            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false
            binding.btnLogin.text = "Logging in..."

            lifecycleScope.launch {
                try {
                    Log.d("TrackWiseDebug", "Attempting login for: $username")

                    val user = database.userDao().login(username, password)

                    withContext(Dispatchers.Main) {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Get Started"

                        if (user != null) {
                            // Login successful
                            currentUserId = user.id
                            sharedPrefs.edit().putLong("userId", user.id).apply()
                            Toast.makeText(this@MainActivity, "Welcome back, $username!", Toast.LENGTH_SHORT).show()
                            setupAppContent()
                            loadUserProfile()
                        } else {
                            // Check if username exists to give specific error
                            lifecycleScope.launch {
                                val usernameExists = database.userDao().usernameExists(username)
                                withContext(Dispatchers.Main) {
                                    if (!usernameExists) {
                                        Toast.makeText(this@MainActivity, "Username not found. Please create an account.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Invalid password. Please try again.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TrackWiseDebug", "Login error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = "Get Started"
                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.tvCreateAccount.setOnClickListener {
            showCreateAccountDialog()
        }
    }

    // ============================================================
    // CREATE ACCOUNT DIALOG - UPDATED WITH DEBUG LOGGING
    // ============================================================

    /**
     * Shows dialog for creating a new user account
     *
     * This dialog collects:
     * - Username (must be unique)
     * - Password
     * - Password confirmation (to prevent typos)
     *
     * On successful creation:
     * - Creates user in database
     * - Creates default expense categories (Food, Transport, etc.)
     * - Auto-logs in the new user
     *
     * IMPORTANT: The dialog uses SOFT_INPUT_ADJUST_RESIZE to prevent
     * the keyboard from squishing the dialog content.
     *
     * FIXED: Added proper password validation and debug logging
     */
    private fun showCreateAccountDialog() {
        // Inflate the dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_account, null)

        // Find input fields
        val etNewUsername = dialogView.findViewById<TextInputEditText>(R.id.etNewUsername)
        val etNewPassword = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        // Build the dialog
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Create New Account")
            .setView(dialogView)
            .setPositiveButton("Create", null) // Set to null initially to override later
            .setNegativeButton("Cancel", null)
            .create()

        // Prevent keyboard from squishing the dialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()

        // Override the Positive Button click listener to prevent auto-dismissal on validation error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // Get user input - trim username but NOT password
            val username = etNewUsername.text.toString().trim().lowercase()
            val password = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            Log.d("TrackWiseDebug", "CREATE ACCOUNT: User='$username', PassLen=${password.length}, ConfirmLen=${confirmPassword.length}")

            // ============================================================
            // VALIDATION CHECKS
            // ============================================================

            if (username.isEmpty() || username.length < 3) {
                etNewUsername.error = "Username must be at least 3 characters"
                return@setOnClickListener
            }

            if (password.isEmpty() || password.length < 4) {
                etNewPassword.error = "Password must be at least 4 characters"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                etConfirmPassword.error = "Passwords do not match!"
                Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ============================================================
            // CREATE ACCOUNT IN DATABASE
            // ============================================================

            lifecycleScope.launch {
                try {
                    val exists = database.userDao().usernameExists(username)

                    if (exists) {
                        withContext(Dispatchers.Main) {
                            etNewUsername.error = "Username already exists"
                        }
                    } else {
                        val userId = database.userDao().insertUser(
                            User(
                                username = username,
                                password = password,
                                monthlyMinGoal = 0.0,
                                monthlyMaxGoal = 10000.0
                            )
                        )

                        if (userId > 0) {
                            // Create default categories
                            val defaultCategories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Healthcare")
                            defaultCategories.forEach { categoryName ->
                                database.categoryDao().insertCategory(
                                    Category(name = categoryName, userId = userId)
                                )
                            }

                            withContext(Dispatchers.Main) {
                                currentUserId = userId
                                sharedPrefs.edit().putLong("userId", userId).apply()
                                Toast.makeText(this@MainActivity, "Welcome, $username!", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                setupAppContent()
                                loadUserProfile()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TrackWiseDebug", "Error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error creating account", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Auto-focus on username field
        etNewUsername.requestFocus()
    }

    // ============================================================
    // APP CONTENT & FRAGMENT MANAGEMENT
    // ============================================================

    /** Sets up main app content after login */
    private fun setupAppContent() {
        binding.loginLayout.visibility = android.view.View.GONE
        binding.appContent.visibility = android.view.View.VISIBLE
        loadFragment(DashboardFragment())
    }

    /** Loads a fragment into the container */
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Shows dialog for setting monthly budget goals */
    private fun showSetBudgetDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_budget, null)

        val etMinGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMinGoal)
        val etMaxGoal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMaxGoal)

        // Load existing goals
        val minGoal = sharedPrefs.getFloat("minGoal", 0f)
        val maxGoal = sharedPrefs.getFloat("maxGoal", 10000f)
        etMinGoal.setText(minGoal.toString())
        etMaxGoal.setText(maxGoal.toString())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Set Monthly Budget Goals")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val minGoalValue = etMinGoal.text.toString().toDoubleOrNull() ?: 0.0
                val maxGoalValue = etMaxGoal.text.toString().toDoubleOrNull() ?: 10000.0

                sharedPrefs.edit()
                    .putFloat("minGoal", minGoalValue.toFloat())
                    .putFloat("maxGoal", maxGoalValue.toFloat())
                    .apply()

                Toast.makeText(this, "Budget goals updated!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }
}