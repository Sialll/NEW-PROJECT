package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickTemplateDao {
    @Query("SELECT * FROM quick_templates WHERE enabled = 1 ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<QuickTemplateEntity>>

    @Query("SELECT * FROM quick_templates WHERE enabled = 1")
    suspend fun getAll(): List<QuickTemplateEntity>

    @Query("SELECT * FROM quick_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): QuickTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuickTemplateEntity)

    @Query("DELETE FROM quick_templates WHERE id = :id")
    suspend fun deleteById(id: String)
}
