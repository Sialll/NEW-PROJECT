package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_templates")
data class QuickTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val amount: Long,
    val description: String,
    val merchant: String?,
    val category: String,
    val spendingKind: String,
    val repeatMonthlyDay: Int?,
    val enabled: Boolean,
    val createdAtMillis: Long
)
