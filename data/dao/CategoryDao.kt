package com.trackwiseai.data.dao

import androidx.room.*
import com.trackwiseai.data.entities.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insertCategory(category: Category): Long

    @Query("SELECT * FROM categories WHERE userId = :userId")
    fun getCategories(userId: Long): Flow<List<Category>>

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT name FROM categories WHERE id = :categoryId")
    suspend fun getCategoryName(categoryId: Long): String
}