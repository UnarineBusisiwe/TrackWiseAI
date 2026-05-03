package com.trackwiseai.data.dao

import androidx.room.*
import com.trackwiseai.data.entities.User
import kotlinx.coroutines.flow.Flow

/**
 * UserDao - Data Access Object for User entity
 *
 * This interface defines all database operations for the User table.
 * Room generates the implementation code at compile time.
 *
 * All suspend functions must be called from a coroutine (lifecycleScope.launch)
 * to prevent blocking the UI thread.
 *
 * @author TrackWise AI Team
 * @version 1.0
 */
@Dao
interface UserDao {

    // ============================================================
    // CREATE (Insert) Operations
    // ============================================================

    /**
     * Insert a new user into the database
     *
     * @param user The User object to insert
     * @return The auto-generated ID of the new user
     *
     * Usage: val userId = userDao.insertUser(User(username = "john", password = "123"))
     */
    @Insert
    suspend fun insertUser(user: User): Long

    /**
     * Insert multiple users at once (batch insert)
     *
     * @param users List of User objects to insert
     * @return List of auto-generated IDs
     */
    @Insert
    suspend fun insertAllUsers(vararg users: User): List<Long>

    // ============================================================
    // READ (Query) Operations
    // ============================================================

    /**
     * Authenticate a user with username and password
     *
     * @param username The user's username (case-sensitive)
     * @param password The user's password
     * @return User object if credentials match, null otherwise
     *
     * Usage: val user = userDao.login("john", "password123")
     */
    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    /**
     * Get a single user by their ID (returns Flow for real-time updates)
     *
     * @param userId The user's unique ID
     * @return Flow<User> that emits updates when the user data changes
     *
     * Usage: userDao.getUser(userId).collect { user -> updateUI(user) }
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUser(userId: Long): Flow<User>

    /**
     * Get a single user by their ID (single result, no Flow)
     *
     * @param userId The user's unique ID
     * @return User object or null if not found
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): User?

    /**
     * Get a user by their username
     *
     * @param username The username to search for
     * @return User object or null if not found
     */
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    /**
     * Get all users from the database
     *
     * @return List of all User objects
     *
     * Usage: val allUsers = userDao.getAllUsers()
     */
    @Query("SELECT * FROM users ORDER BY username ASC")
    suspend fun getAllUsers(): List<User>

    /**
     * Get all users as Flow (real-time updates)
     *
     * @return Flow<List<User>> that emits when any user changes
     */
    @Query("SELECT * FROM users ORDER BY username ASC")
    fun getAllUsersFlow(): Flow<List<User>>

    /**
     * Check if a username already exists in the database
     *
     * @param username The username to check
     * @return true if username exists, false otherwise
     *
     * Usage: val exists = userDao.usernameExists("john")
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    suspend fun usernameExists(username: String): Boolean

    /**
     * Get the total number of users in the database
     *
     * @return Count of all users
     */
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    // ============================================================
    // UPDATE Operations
    // ============================================================

    /**
     * Update an existing user in the database
     *
     * @param user The User object with updated values (must have valid ID)
     *
     * Usage: userDao.updateUser(user.copy(password = "newPassword"))
     */
    @Update
    suspend fun updateUser(user: User)

    /**
     * Update multiple users at once
     *
     * @param users List of User objects to update
     */
    @Update
    suspend fun updateAllUsers(vararg users: User)

    /**
     * Update a user's password
     *
     * @param userId The user's ID
     * @param newPassword The new password to set
     */
    @Query("UPDATE users SET password = :newPassword WHERE id = :userId")
    suspend fun updatePassword(userId: Long, newPassword: String)

    /**
     * Update a user's budget goals
     *
     * @param userId The user's ID
     * @param minGoal New minimum spending goal
     * @param maxGoal New maximum spending goal
     */
    @Query("UPDATE users SET monthlyMinGoal = :minGoal, monthlyMaxGoal = :maxGoal WHERE id = :userId")
    suspend fun updateBudgetGoals(userId: Long, minGoal: Double, maxGoal: Double)

    // ============================================================
    // DELETE Operations
    // ============================================================

    /**
     * Delete a single user from the database
     *
     * @param user The User object to delete (must have valid ID)
     */
    @Delete
    suspend fun deleteUser(user: User)

    /**
     * Delete a user by their ID
     *
     * @param userId The ID of the user to delete
     * @return Number of rows deleted (should be 1 if successful)
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Long): Int

    /**
     * Delete a user by their username
     *
     * @param username The username of the user to delete
     * @return Number of rows deleted (should be 1 if successful)
     */
    @Query("DELETE FROM users WHERE username = :username")
    suspend fun deleteUserByUsername(username: String): Int

    /**
     * Delete ALL users from the database (use with caution!)
     * This is primarily for testing and debugging purposes.
     *
     * @return Number of rows deleted
     */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers(): Int

    // ============================================================
    // CUSTOM QUERIES for Dashboard & Analytics
    // ============================================================

    /**
     * Get all users who have exceeded their budget
     * Useful for admin features or notifications
     *
     * @param currentSpending The current spending amount to compare
     * @return List of users who have exceeded their max goal
     */
    @Query("SELECT * FROM users WHERE monthlyMaxGoal > 0 AND monthlyMaxGoal < :currentSpending")
    suspend fun getUsersExceedingBudget(currentSpending: Double): List<User>

    /**
     * Get users who are under their minimum spending goal
     * (Good savers - for achievement badges)
     *
     * @param currentSpending The current spending amount to compare
     * @return List of users under their minimum goal
     */
    @Query("SELECT * FROM users WHERE monthlyMinGoal > 0 AND monthlyMinGoal > :currentSpending")
    suspend fun getUsersUnderMinimumGoal(currentSpending: Double): List<User>

    /**
     * Search users by username pattern (for search feature)
     *
     * @param query The search query (use % for wildcard)
     * @return List of users matching the pattern
     *
     * Usage: userDao.searchUsers("%john%")
     */
    @Query("SELECT * FROM users WHERE username LIKE :query ORDER BY username ASC")
    suspend fun searchUsers(query: String): List<User>
}