package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_closings")
data class MonthlyClosingEntity(
    @PrimaryKey val month: String,
    val carryIn: Long,
    val expectedClosing: Long,
    val actualClosing: Long?,
    val delta: Long?,
    val closedAtMillis: Long?
)
