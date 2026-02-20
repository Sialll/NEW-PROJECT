package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyClosingDao {
    @Query("SELECT * FROM monthly_closings ORDER BY month DESC")
    fun observeAll(): Flow<List<MonthlyClosingEntity>>

    @Query("SELECT * FROM monthly_closings WHERE month = :month LIMIT 1")
    suspend fun getByMonth(month: String): MonthlyClosingEntity?

    @Query("SELECT * FROM monthly_closings WHERE month < :month ORDER BY month DESC LIMIT 1")
    suspend fun getLatestBefore(month: String): MonthlyClosingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MonthlyClosingEntity)
}
