package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarMemoDao {
    @Query("SELECT * FROM calendar_memos ORDER BY date DESC")
    fun observeAll(): Flow<List<CalendarMemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarMemoEntity)

    @Query("DELETE FROM calendar_memos WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM calendar_memos")
    suspend fun deleteAll()

    @Query("DELETE FROM calendar_memos WHERE date >= :startDate AND date <= :endDate")
    suspend fun deleteByDateRange(startDate: String, endDate: String)
}
