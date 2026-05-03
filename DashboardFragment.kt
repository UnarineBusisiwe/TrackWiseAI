package com.trackwiseai

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.Expense
import com.trackwiseai.databinding.FragmentDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private var userId: Long = -1
    private lateinit var expenseAdapter: ExpenseAdapter
    private var currentStartDate: Date = Date()
    private var currentEndDate: Date = Date()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getInstance(requireContext())
        
        val sharedPrefs = requireActivity().getSharedPreferences("TrackWisePrefs", Context.MODE_PRIVATE)
        userId = sharedPrefs.getLong("userId", -1)

        if (userId == -1L) {
            return
        }

        setupRecyclerView()
        setupClickListeners()

        // Set default period to current month
        setDefaultPeriod()
        loadDashboardData()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter { expense ->
            showExpenseDetailsDialog(expense)
        }
        binding.rvRecentExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectPeriod.setOnClickListener {
            showPeriodSelectorDialog()
        }
    }

    private fun setDefaultPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        currentEndDate = calendar.time
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            // Get total spent in period
            val totalSpent = database.expenseDao().getTotalSpentBetweenDates(
                userId, currentStartDate, currentEndDate
            ) ?: 0.0

            // Get recent expenses
            val expensesFlow = database.expenseDao().getExpensesBetweenDates(
                userId, currentStartDate, currentEndDate
            )

            // Update UI
            updateBudgetUI(totalSpent)

            // Update expenses list
            expensesFlow.collect { expenseList ->
                expenseAdapter.submitList(expenseList.take(5))
            }
        }
    }

    private fun updateBudgetUI(totalSpent: Double) {
        val sharedPrefs = requireActivity().getSharedPreferences("TrackWisePrefs", Context.MODE_PRIVATE)
        val minGoal = sharedPrefs.getFloat("minGoal", 0f).toDouble()
        val maxGoal = sharedPrefs.getFloat("maxGoal", 10000f).toDouble()

        val percentage = if (maxGoal > 0) (totalSpent / maxGoal * 100).toInt() else 0
        binding.tvBudgetPercentage.text = "$percentage%"
        binding.progressBudget.setProgress(percentage, true)
        binding.tvSpentAmount.text = formatCurrency(totalSpent)
        binding.tvGoalRange.text = "Min: ${formatCurrency(minGoal)} | Max: ${formatCurrency(maxGoal)}"

        if (totalSpent > maxGoal) {
            Toast.makeText(requireContext(), "Warning: Budget exceeded!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPeriodSelectorDialog() {
        val options = arrayOf("Today", "This Week", "This Month", "Last Month")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Period")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setTodayPeriod()
                    1 -> setThisWeekPeriod()
                    2 -> setThisMonthPeriod()
                    3 -> setLastMonthPeriod()
                }
            }
            .show()
    }

    private fun setTodayPeriod() {
        val calendar = Calendar.getInstance()
        currentStartDate = calendar.time
        currentEndDate = calendar.time
        loadDashboardData()
    }

    private fun setThisWeekPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getActualMaximum(Calendar.DAY_OF_WEEK))
        currentEndDate = calendar.time
        loadDashboardData()
    }

    private fun setThisMonthPeriod() {
        setDefaultPeriod()
        loadDashboardData()
    }

    private fun setLastMonthPeriod() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        currentStartDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        currentEndDate = calendar.time
        loadDashboardData()
    }

    private fun showExpenseDetailsDialog(expense: Expense) {
        lifecycleScope.launch {
            val categoryName = database.categoryDao().getCategoryName(expense.categoryId)
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            
            val message = "Amount: ${formatCurrency(expense.amount)}\n" +
                          "Category: $categoryName\n" +
                          "Date: ${dateFormat.format(expense.date)}\n" +
                          "Description: ${expense.description}"

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Expense Details")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        return format.format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}