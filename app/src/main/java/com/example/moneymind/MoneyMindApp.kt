package com.example.moneymind

import android.app.Application
import com.example.moneymind.core.ServiceLocator

class MoneyMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
