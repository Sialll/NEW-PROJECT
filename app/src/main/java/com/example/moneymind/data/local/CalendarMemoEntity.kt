package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_memos")
data class CalendarMemoEntity(
    @PrimaryKey val date: String,
    val memo: String,
    val updatedAtMillis: Long
)
