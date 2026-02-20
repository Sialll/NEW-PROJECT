package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owned_accounts")
data class OwnedAccountEntity(
    @PrimaryKey val accountMask: String,
    val bank: String,
    val ownerName: String
)
