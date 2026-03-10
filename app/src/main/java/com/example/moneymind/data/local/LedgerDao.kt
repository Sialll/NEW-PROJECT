package com.example.moneymind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY occurredAtMillis DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries")
    suspend fun getAll(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entries ORDER BY occurredAtMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LedgerEntryEntity>

    @Query("SELECT fingerprint FROM ledger_entries WHERE fingerprint IN (:fingerprints)")
    suspend fun getExistingFingerprints(fingerprints: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entries: List<LedgerEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LedgerEntryEntity)

    @Query(
        """
        UPDATE ledger_entries
        SET fingerprint = :fingerprint,
            occurredAtMillis = :occurredAtMillis,
            type = :type,
            amount = :amount,
            category = :category,
            description = :description,
            merchant = :merchant,
            spendingKind = :spendingKind,
            countedInExpense = :countedInExpense,
            baseType = :baseType,
            baseCategory = :baseCategory,
            baseSpendingKind = :baseSpendingKind,
            baseCountedInExpense = :baseCountedInExpense
        WHERE id = :id
        """
    )
    suspend fun updateEntryById(
        id: String,
        fingerprint: String,
        occurredAtMillis: Long,
        type: String,
        amount: Long,
        category: String,
        description: String,
        merchant: String?,
        spendingKind: String,
        countedInExpense: Boolean,
        baseType: String,
        baseCategory: String,
        baseSpendingKind: String,
        baseCountedInExpense: Boolean
    )

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM ledger_entries
        WHERE occurredAtMillis >= :startMillisInclusive
          AND occurredAtMillis < :endMillisExclusive
        """
    )
    suspend fun deleteByOccurredAtRange(
        startMillisInclusive: Long,
        endMillisExclusive: Long
    )
}
