package com.trackwiseai

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.Category
import com.trackwiseai.data.entities.Expense
import com.trackwiseai.databinding.ActivityAddExpenseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var database: AppDatabase
    private var userId: Long = -1
    private var selectedCategoryId: Long = -1
    private var categories: List<Category> = emptyList()
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedStartTime: Calendar = Calendar.getInstance()
    private var selectedEndTime: Calendar = Calendar.getInstance()
    private var photoUri: Uri? = null
    private var currentPhotoPath: String? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            photoUri?.let { uri ->
                binding.ivPhoto.setImageURI(uri)
                currentPhotoPath = getRealPathFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = AppDatabase.getInstance(this)
        userId = intent.getLongExtra("userId", -1)

        if (userId == -1L) {
            finish()
            return
        }

        setupClickListeners()
        loadCategories()
    }

    private fun setupClickListeners() {
        binding.btnSaveExpense.setOnClickListener {
            saveExpense()
        }

        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        binding.etStartTime.setOnClickListener {
            showStartTimePicker()
        }

        binding.etEndTime.setOnClickListener {
            showEndTimePicker()
        }

        binding.etCategory.setOnClickListener {
            showCategorySelector()
        }

        binding.btnTakePhoto.setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            database.categoryDao().getCategories(userId).collect { categoryList ->
                categories = categoryList
                if (categories.isNotEmpty()) {
                    selectedCategoryId = categories[0].id
                    binding.etCategory.setText(categories[0].name)
                } else {
                    // Prompt to add category
                    showAddCategoryDialog()
                }
            }
        }
    }

    private fun showCategorySelector() {
        val categoryNames = categories.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Category")
            .setItems(categoryNames) { _, which ->
                selectedCategoryId = categories[which].id
                binding.etCategory.setText(categories[which].name)
            }
            .setNeutralButton("Add New") { _, _ ->
                showAddCategoryDialog()
            }
            .show()
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val etCategoryName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCategoryName)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = etCategoryName.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val category = Category(name = categoryName, userId = userId)
                        val id = database.categoryDao().insertCategory(category)
                        selectedCategoryId = id
                        binding.etCategory.setText(categoryName)
                        loadCategories() // Reload categories
                        Toast.makeText(this@AddExpenseActivity, "Category added", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog.newInstance(
            { _, year, monthOfYear, dayOfMonth ->
                selectedDate.set(year, monthOfYear, dayOfMonth)
                val format = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                binding.etDate.setText(format.format(selectedDate.time))
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        dpd.show(supportFragmentManager, "DatePicker")
    }

    private fun showStartTimePicker() {
        val now = Calendar.getInstance()
        val tpd = TimePickerDialog.newInstance(
            { _, hourOfDay, minute, _ ->
                selectedStartTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedStartTime.set(Calendar.MINUTE, minute)
                binding.etStartTime.setText(String.format("%02d:%02d", hourOfDay, minute))
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        )
        tpd.show(supportFragmentManager, "StartTimePicker")
    }

    private fun showEndTimePicker() {
        val now = Calendar.getInstance()
        val tpd = TimePickerDialog.newInstance(
            { _, hourOfDay, minute, _ ->
                selectedEndTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedEndTime.set(Calendar.MINUTE, minute)
                binding.etEndTime.setText(String.format("%02d:%02d", hourOfDay, minute))
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        )
        tpd.show(supportFragmentManager, "EndTimePicker")
    }

    private fun dispatchTakePictureIntent() {
        val photoFile = createImageFile()
        photoFile?.let {
            photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            takePictureLauncher.launch(takePictureIntent)
        }
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, "JPEG_${timeStamp}_${userId}.jpg").apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return currentPhotoPath
    }

    private fun saveExpense() {
        val amountStr = binding.etAmount.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter description", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategoryId == -1L) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val expense = Expense(
            amount = amount,
            date = selectedDate.time,
            startTime = binding.etStartTime.text.toString(),
            endTime = binding.etEndTime.text.toString(),
            description = description,
            categoryId = selectedCategoryId,
            userId = userId,
            photoPath = currentPhotoPath
        )

        lifecycleScope.launch {
            try {
                database.expenseDao().insertExpense(expense)
                Toast.makeText(this@AddExpenseActivity, "Expense saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddExpenseActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}