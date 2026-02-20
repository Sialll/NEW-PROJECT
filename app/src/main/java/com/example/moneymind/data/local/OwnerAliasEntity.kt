package com.example.moneymind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "owner_aliases")
data class OwnerAliasEntity(
    @PrimaryKey val alias: String
)
