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
import com.trackwiseai.databinding.FragmentViewExpenseBinding
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.Expense
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ViewExpenseFragment : Fragment() {

    private var _binding: FragmentViewExpenseBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private lateinit var expenseAdapter: ExpenseAdapter
    private var userId: Long = -1
    private var allExpenses: List<Expense> = emptyList()

    private var startDate: Date = Date()
    private var endDate: Date = Date()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getInstance(requireContext())

        val sharedPrefs = requireActivity().getSharedPreferences("TrackWisePrefs", Context.MODE_PRIVATE)
        userId = sharedPrefs.getLong("userId", -1)

        if (userId == -1L) {
            Toast.makeText(requireContext(), "Please log in again", Toast.LENGTH_SHORT).show()
            return
        }

        setupRecyclerView()
        setupClickListeners()

        // Set default period to current month
        setCurrentMonthPeriod()
        loadExpenses()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter { expense ->
            showExpenseDetailsDialog(expense)
        }
        binding.rvExpenses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expenseAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnStartDate.setOnClickListener {
            showStartDatePicker()
        }

        binding.btnEndDate.setOnClickListener {
            showEndDatePicker()
        }

        binding.btnFilter.setOnClickListener {
            loadExpenses()
        }

        binding.btnExportReport.setOnClickListener {
            showReportOptions()
        }
    }

    private fun setCurrentMonthPeriod() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        startDate = calendar.time
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        endDate = calendar.time

        updateDateButtons()
    }

    private fun updateDateButtons() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.btnStartDate.text = dateFormat.format(startDate)
        binding.btnEndDate.text = dateFormat.format(endDate)
    }

    private fun showStartDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        val dpd = DatePickerDialog.newInstance(
            { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                startDate = calendar.time
                updateDateButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show(parentFragmentManager, "StartDatePicker")
    }

    private fun showEndDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = endDate
        val dpd = DatePickerDialog.newInstance(
            { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                endDate = calendar.time
                updateDateButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show(parentFragmentManager, "EndDatePicker")
    }

    private fun loadExpenses() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val expenses = database.expenseDao().getExpensesBetweenDates(
                    userId, startDate, endDate
                )

                expenses.collect { expenseList ->
                    allExpenses = expenseList
                    expenseAdapter.submitList(expenseList)
                    updateTotalAndSummary(expenseList)
                    binding.progressBar.visibility = View.GONE

                    if (expenseList.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvExpenses.visibility = View.GONE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvExpenses.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error loading expenses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTotalAndSummary(expenses: List<Expense>) {
        val totalSpent = expenses.sumOf { it.amount }
        binding.tvTotalSpent.text = formatCurrency(totalSpent)
        binding.tvTransactionCount.text = "${expenses.size} transactions"

        // Get unique categories count
        val uniqueCategories = expenses.map { it.categoryId }.distinct().size
        binding.tvCategoriesCount.text = "$uniqueCategories categories"
    }

    private fun showExpenseDetailsDialog(expense: Expense) {
        lifecycleScope.launch {
            try {
                val categoryName = database.categoryDao().getCategoryName(expense.categoryId)
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                val message = buildString {
                    append("📝 Description: ${expense.description}\n\n")
                    append("📂 Category: $categoryName\n")
                    append("💰 Amount: ${formatCurrency(expense.amount)}\n")
                    append("📅 Date: ${dateFormat.format(expense.date)}\n")
                    append("⏰ Time: ${expense.startTime} - ${expense.endTime}\n")
                    if (expense.photoPath != null) {
                        append("\n📷 Photo saved at: ${expense.photoPath}")
                    }
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Expense Details")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> }
                    .setNeutralButton("Delete") { _, _ ->
                        confirmDeleteExpense(expense)
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteExpense(expense: Expense) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Expense")
            .setMessage("Are you sure you want to delete this expense?")
            .setPositiveButton("Delete") { _, _ ->
                deleteExpense(expense)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExpense(expense: Expense) {
        lifecycleScope.launch {
            try {
                database.expenseDao().deleteExpense(expense)
                Toast.makeText(requireContext(), "Expense deleted", Toast.LENGTH_SHORT).show()
                loadExpenses() // Reload the list
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error deleting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReportOptions() {
        val options = arrayOf("View by Category", "Export Summary", "Share Report")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Report Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCategoryBreakdown()
                    1 -> showExportSummary()
                    2 -> shareReport()
                }
            }
            .show()
    }

    private fun showCategoryBreakdown() {
        lifecycleScope.launch {
            try {
                val categorySpending = database.expenseDao().getSpentByCategory(userId, startDate, endDate)

                if (categorySpending.isEmpty()) {
                    Toast.makeText(requireContext(), "No expenses in this period", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val message = buildString {
                    append("📊 CATEGORY BREAKDOWN\n")
                    append("${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(startDate)} - ")
                    append("${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(endDate)}\n\n")
                    categorySpending.forEach {
                        append("${it.categoryName}: ${formatCurrency(it.totalSpent)}\n")
                    }
                    append("\nTotal: ${formatCurrency(categorySpending.sumOf { it.totalSpent })}")
                }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Spending by Category")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportSummary() {
        val totalSpent = allExpenses.sumOf { it.amount }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val summary = """
            TRACKWISE AI EXPENSE REPORT
            ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}
            
            Total Expenses: ${allExpenses.size}
            Total Spent: ${formatCurrency(totalSpent)}
            Average per Transaction: ${formatCurrency(if (allExpenses.isNotEmpty()) totalSpent / allExpenses.size else 0.0)}
            
            Generated by TrackWise AI
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Expense Summary")
            .setMessage(summary)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard(summary)
            }
            .show()
    }

    private fun shareReport() {
        val totalSpent = allExpenses.sumOf { it.amount }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val shareText = """
            TrackWise AI Expense Report
            Period: ${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}
            Total Expenses: ${allExpenses.size}
            Total Spent: ${formatCurrency(totalSpent)}
        """.trimIndent()

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Share Report"))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Expense Report", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        format.currency = java.util.Currency.getInstance("ZAR")
        return format.format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}