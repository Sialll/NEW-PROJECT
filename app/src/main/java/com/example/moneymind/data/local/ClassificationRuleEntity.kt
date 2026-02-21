package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classification_rules")
data class ClassificationRuleEntity(
    @PrimaryKey val id: String,
    val keyword: String,
    val spendingKind: String,
    val category: String,
    val forcedType: String?,
    val enabled: Boolean,
    val createdAtMillis: Long
)
