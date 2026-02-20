package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["occurredAtMillis"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val fingerprint: String,
    val occurredAtMillis: Long,
    val amount: Long,
    val type: String,
    val category: String,
    val description: String,
    val merchant: String?,
    val source: String,
    val spendingKind: String,
    val countedInExpense: Boolean,
    val accountMask: String?,
    val counterpartyName: String?
)
