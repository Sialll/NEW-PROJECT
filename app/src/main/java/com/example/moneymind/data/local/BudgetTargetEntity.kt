package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_targets")
data class BudgetTargetEntity(
    @PrimaryKey val key: String,
    val category: String?,
    val amount: Long
)
