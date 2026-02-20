package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnedAccountDao {
    @Query("SELECT * FROM owned_accounts ORDER BY ownerName, bank")
    fun observeAll(): Flow<List<OwnedAccountEntity>>

    @Query("SELECT * FROM owned_accounts")
    suspend fun getAll(): List<OwnedAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnedAccountEntity)
}
