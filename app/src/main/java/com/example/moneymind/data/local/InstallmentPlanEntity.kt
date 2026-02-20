package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installment_plans")
data class InstallmentPlanEntity(
    @PrimaryKey val id: String,
    val cardLast4: String,
    val merchant: String,
    val monthlyAmount: Long,
    val totalMonths: Int,
    val startMonth: String
)
