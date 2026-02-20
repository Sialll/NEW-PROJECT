package com.example.moneymind.notification

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object NotificationAccessHelper {
    fun isAccessGranted(context: Context, serviceClass: Class<*>): Boolean {
        val enabledValue = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        if (enabledValue.isBlank()) return false

        val component = ComponentName(context, serviceClass)
        val fullName = component.flattenToString()
        val shortName = component.flattenToShortString()

        return enabledValue.split(':').any { item ->
            item.equals(fullName, ignoreCase = true) || item.equals(shortName, ignoreCase = true)
        }
    }
}
