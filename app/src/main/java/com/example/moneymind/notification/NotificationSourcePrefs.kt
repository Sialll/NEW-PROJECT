package com.example.moneymind.notification

import android.content.Context

object NotificationSourcePrefs {
    private const val PREFS_NAME = "moneymind_preferences"
    private const val PREF_ALLOWED_PACKAGES = "notification_allowed_packages_v1"

    fun hasSavedFilter(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PREF_ALLOWED_PACKAGES)
    }

    fun loadAllowedPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREF_ALLOWED_PACKAGES, emptySet())
            ?.asSequence()
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    fun saveAllowedPackages(context: Context, packages: Set<String>) {
        val normalized = packages
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_ALLOWED_PACKAGES, normalized)
            .apply()
    }
}
