package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassificationRuleDao {
    @Query("SELECT * FROM classification_rules WHERE enabled = 1 ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<ClassificationRuleEntity>>

    @Query("SELECT * FROM classification_rules WHERE enabled = 1 ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<ClassificationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClassificationRuleEntity)

    @Query("DELETE FROM classification_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
