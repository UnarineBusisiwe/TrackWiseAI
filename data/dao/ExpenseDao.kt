package com.trackwiseai.data.dao

import androidx.room.*
import com.trackwiseai.data.entities.Expense
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesBetweenDates(userId: Long, startDate: Date, endDate: Date): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalSpentBetweenDates(userId: Long, startDate: Date, endDate: Date): Double?

    @Query("""
        SELECT c.name as categoryName, SUM(e.amount) as totalSpent 
        FROM expenses e 
        JOIN categories c ON e.categoryId = c.id 
        WHERE e.userId = :userId AND e.date BETWEEN :startDate AND :endDate 
        GROUP BY c.name
    """)
    suspend fun getSpentByCategory(userId: Long, startDate: Date, endDate: Date): List<CategorySpent>

    @Delete
    suspend fun deleteExpense(expense: Expense)
}

data class CategorySpent(
    val categoryName: String,
    val totalSpent: Double
)