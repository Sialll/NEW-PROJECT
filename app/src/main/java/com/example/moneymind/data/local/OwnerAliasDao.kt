package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OwnerAliasDao {
    @Query("SELECT * FROM owner_aliases ORDER BY alias")
    fun observeAll(): Flow<List<OwnerAliasEntity>>

    @Query("SELECT * FROM owner_aliases")
    suspend fun getAll(): List<OwnerAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OwnerAliasEntity)
}
