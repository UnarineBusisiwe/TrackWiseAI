package com.trackwiseai.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val password: String,
    val monthlyMinGoal: Double = 0.0,
    val monthlyMaxGoal: Double = 0.0
)