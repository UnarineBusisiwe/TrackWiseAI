package com.trackwiseai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trackwiseai.data.AppDatabase
import com.trackwiseai.data.entities.Category
import com.trackwiseai.data.entities.Expense
import com.trackwiseai.databinding.FragmentAddExpenseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseFragment : Fragment() {

    private var _binding: FragmentAddExpenseBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: AppDatabase
    private var userId: Long = -1
    private var selectedCategoryId: Long = -1
    private var categories: List<Category> = emptyList()

    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedStartTime: Calendar = Calendar.getInstance()
    private var selectedEndTime: Calendar = Calendar.getInstance()

    private var photoUri: Uri? = null
    private var currentPhotoPath: String? = null

    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            photoUri?.let { uri ->
                binding.ivPhoto.setImageURI(uri)
                currentPhotoPath = getRealPathFromUri(uri)
                Toast.makeText(requireContext(), "Photo captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = AppDatabase.getInstance(requireContext())

        val sharedPrefs = requireActivity().getSharedPreferences("TrackWisePrefs", Context.MODE_PRIVATE)
        userId = sharedPrefs.getLong("userId", -1)

        if (userId == -1L) {
            Toast.makeText(requireContext(), "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        setupClickListeners()
        loadCategories()
        setDefaultDateAndTimes()
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

    private fun setDefaultDateAndTimes() {
        val today = Calendar.getInstance()
        selectedDate = today

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.etDate.setText(dateFormat.format(today.time))

        binding.etStartTime.setText(String.format("%02d:%02d", today.get(Calendar.HOUR_OF_DAY), today.get(Calendar.MINUTE)))
        selectedStartTime = today

        val endTime = Calendar.getInstance()
        endTime.add(Calendar.HOUR_OF_DAY, 1)
        binding.etEndTime.setText(String.format("%02d:%02d", endTime.get(Calendar.HOUR_OF_DAY), endTime.get(Calendar.MINUTE)))
        selectedEndTime = endTime
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            database.categoryDao().getCategories(userId).collect { categoryList ->
                categories = categoryList
                if (categories.isNotEmpty()) {
                    selectedCategoryId = categories[0].id
                    binding.etCategory.setText(categories[0].name)
                } else {
                    binding.etCategory.setText("Tap to add category")
                }
            }
        }
    }

    private fun showCategorySelector() {
        if (categories.isEmpty()) {
            showAddCategoryDialog()
            return
        }

        val categoryNames = categories.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = etCategoryName.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val category = Category(name = categoryName, userId = userId)
                        val id = database.categoryDao().insertCategory(category)
                        selectedCategoryId = id
                        binding.etCategory.setText(categoryName)
                        loadCategories()
                        Toast.makeText(requireContext(), "Category added", Toast.LENGTH_SHORT).show()
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
        dpd.show(parentFragmentManager, "DatePicker")
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
        tpd.show(parentFragmentManager, "StartTimePicker")
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
        tpd.show(parentFragmentManager, "EndTimePicker")
    }

    private fun dispatchTakePictureIntent() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }
        takePhoto()
    }

    private fun takePhoto() {
        val photoFile = createImageFile()
        photoFile?.let {
            photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                it
            )
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            takePictureLauncher.launch(takePictureIntent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File(storageDir, "JPEG_${timeStamp}_${userId}.jpg").apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return currentPhotoPath
    }

    private fun saveExpense() {
        val amountStr = binding.etAmount.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), "Enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(requireContext(), "Enter description", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCategoryId == -1L) {
            Toast.makeText(requireContext(), "Select category", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Expense saved!", Toast.LENGTH_SHORT).show()
                clearForm()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearForm() {
        binding.etAmount.text?.clear()
        binding.etDescription.text?.clear()
        binding.ivPhoto.setImageResource(android.R.drawable.ic_menu_camera)
        currentPhotoPath = null
        photoUri = null
        setDefaultDateAndTimes()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}