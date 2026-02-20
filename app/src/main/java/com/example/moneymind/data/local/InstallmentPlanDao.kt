package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentPlanDao {
    @Query("SELECT * FROM installment_plans ORDER BY startMonth DESC")
    fun observeAll(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans")
    suspend fun getAll(): List<InstallmentPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InstallmentPlanEntity)
}
