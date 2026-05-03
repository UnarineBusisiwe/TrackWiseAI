package com.trackwiseai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.Expense
import com.trackwiseai.databinding.ActivityDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var database: AppDatabase
    private var userId: Long = -1
    private lateinit var expenseAdapter: ExpenseAdapter
    private var currentStartDate: Date = Date()
    private var currentEndDate: Date = Date()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        userId = intent.getLongExtra("userId", -1)

        if (userId == -1L) {
            finish()
            return
        }

        setupRecyclerView()
        setupClickListeners()

        // Set default period to current month
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        currentEndDate = calendar.time

        loadDashboardData()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter { expense ->
            // Show expense details dialog
            showExpenseDetailsDialog(expense)
        }
        binding.rvRecentExpenses.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = expenseAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddExpense.setOnClickListener {
            val intent = Intent(this, AddExpenseActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent)
        }

        binding.btnSelectPeriod.setOnClickListener {
            showPeriodSelectorDialog()
        }

        // Bottom navigation setup
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        // You can add a bottom navigation view to your layout
        // For now, we'll use FAB for add expense
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            // Get user with budget goals
            val user = database.userDao().getUser(userId).let { flow ->
                // Collect first value - for simplicity, query directly
                database.userDao().login("", "") // This needs improvement
            }

            // Get total spent in period
            val totalSpent = database.expenseDao().getTotalSpentBetweenDates(
                userId, currentStartDate, currentEndDate
            ) ?: 0.0

            // Get recent expenses
            val expenses = database.expenseDao().getExpensesBetweenDates(
                userId, currentStartDate, currentEndDate
            )

            // Update UI
            updateBudgetUI(totalSpent)

            // Update expenses list
            lifecycleScope.launch {
                expenses.collect { expenseList ->
                    expenseAdapter.submitList(expenseList.take(5))
                }
            }
        }
    }

    private fun updateBudgetUI(totalSpent: Double) {
        val user = getUserFromDb()
        val minGoal = user?.monthlyMinGoal ?: 0.0
        val maxGoal = user?.monthlyMaxGoal ?: 10000.0

        val percentage = if (maxGoal > 0) (totalSpent / maxGoal * 100).toInt() else 0
        binding.tvBudgetPercentage.text = "$percentage%"
        binding.progressBudget.setProgress(percentage, true)
        binding.tvSpentAmount.text = formatCurrency(totalSpent)
        binding.tvGoalRange.text = "Min: ${formatCurrency(minGoal)} | Max: ${formatCurrency(maxGoal)}"

        // Show warning if exceeding goals
        if (totalSpent > maxGoal) {
            Toast.makeText(this, "Warning: You have exceeded your monthly budget!", Toast.LENGTH_LONG).show()
        } else if (totalSpent < minGoal && totalSpent > 0) {
            Toast.makeText(this, "Great job! You're spending below your target!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUserFromDb(): com.trackwiseai.data.entities.User? {
        var user: com.trackwiseai.data.entities.User? = null
        lifecycleScope.launch {
            // Simplified - in production use Flow
        }
        return user
    }

    private fun showPeriodSelectorDialog() {
        val options = arrayOf("Today", "This Week", "This Month", "Last Month", "Custom Range")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Period")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setTodayPeriod()
                    1 -> setThisWeekPeriod()
                    2 -> setThisMonthPeriod()
                    3 -> setLastMonthPeriod()
                    4 -> showCustomRangePicker()
                }
            }
            .show()
    }

    private fun setTodayPeriod() {
        val calendar = Calendar.getInstance()
        currentStartDate = calendar.time
        currentEndDate = calendar.time
        loadDashboardData()
        Toast.makeText(this, "Showing today's expenses", Toast.LENGTH_SHORT).show()
    }

    private fun setThisWeekPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getActualMaximum(Calendar.DAY_OF_WEEK))
        currentEndDate = calendar.time
        loadDashboardData()
        Toast.makeText(this, "Showing this week's expenses", Toast.LENGTH_SHORT).show()
    }

    private fun setThisMonthPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        currentEndDate = calendar.time
        loadDashboardData()
        Toast.makeText(this, "Showing this month's expenses", Toast.LENGTH_SHORT).show()
    }

    private fun setLastMonthPeriod() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        currentEndDate = calendar.time
        loadDashboardData()
        Toast.makeText(this, "Showing last month's expenses", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomRangePicker() {
        // For simplicity, just show a toast for now as ExpenseListActivity is not implemented
        Toast.makeText(this, "Custom range picker coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun showExpenseDetailsDialog(expense: Expense) {
        lifecycleScope.launch {
            val categoryName = database.categoryDao().getCategoryName(expense.categoryId)
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val message = buildString {
                append("Description: ${expense.description}\n")
                append("Category: $categoryName\n")
                append("Amount: ${formatCurrency(expense.amount)}\n")
                append("Date: ${dateFormat.format(expense.date)}\n")
                append("Time: ${expense.startTime} - ${expense.endTime}\n")
                if (expense.photoPath != null) {
                    append("\n📷 Photo attached")
                }
            }

            MaterialAlertDialogBuilder(this@DashboardActivity)
                .setTitle("Expense Details")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ -> }
                .setNeutralButton("View Photo") { _, _ ->
                    expense.photoPath?.let {
                        // View photo intent
                        Toast.makeText(this@DashboardActivity, "Photo: $it", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        format.currency = java.util.Currency.getInstance("ZAR")
        return format.format(amount)
    }
}