package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetTargetDao {
    @Query("SELECT * FROM budget_targets ORDER BY key")
    fun observeAll(): Flow<List<BudgetTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetTargetEntity)

    @Query("DELETE FROM budget_targets WHERE key = :key")
    suspend fun deleteByKey(key: String)
}
