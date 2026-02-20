package com.example.moneymind.core

import android.content.Context
import com.example.moneymind.data.local.MoneyMindDatabase
import com.example.moneymind.data.repo.LedgerRepository
import com.example.moneymind.security.SecureTextCipher

object ServiceLocator {
    @Volatile
    private var repository: LedgerRepository? = null

    fun init(context: Context) {
        if (repository != null) return
        synchronized(this) {
            if (repository == null) {
                val db = MoneyMindDatabase.getInstance(context.applicationContext)
                val cipher = SecureTextCipher()
                repository = LedgerRepository(db, cipher)
            }
        }
    }

    fun repository(context: Context): LedgerRepository {
        init(context)
        return checkNotNull(repository)
    }
}
