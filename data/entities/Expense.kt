package com.trackwiseai.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val date: Date,
    val startTime: String,
    val endTime: String,
    val description: String,
    val categoryId: Long,
    val userId: Long,
    val photoPath: String? = null
)
