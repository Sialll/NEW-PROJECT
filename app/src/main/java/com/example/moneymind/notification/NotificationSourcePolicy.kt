package com.example.moneymind.notification

import com.example.moneymind.BuildConfig
import android.content.Context
import android.util.Log

object NotificationSourcePolicy {
    private const val TAG = "MM.NotificationSource"

    data class NotificationSourceApp(
        val packageName: String,
        val label: String
    )

    private val supportedApps = listOf(
        NotificationSourceApp("com.kakaobank.channel", "Kakaobank"),
        NotificationSourceApp("viva.republica.toss", "Toss"),
        NotificationSourceApp("kr.co.kfcc.mobile", "KB국민은행"),
        NotificationSourceApp("com.kbstar.kbbank", "KB Star Bank"),
        NotificationSourceApp("com.shinhan.sbanking", "Shinhan Bank"),
        NotificationSourceApp("com.hanabank.ebk.channel.android.hananbank", "Hana Bank"),
        NotificationSourceApp("com.kbankwith.smartbank", "K-Bank"),
        NotificationSourceApp("com.wooribank.smart.npib", "Woori Bank"),
        NotificationSourceApp("com.ibk.neobanking", "IBK i-ONE"),
        NotificationSourceApp("com.kbcard.cxh.appcard", "KB Pay"),
        NotificationSourceApp("com.shcard.smartpay", "Shinhan Card"),
        NotificationSourceApp("com.hyundaicard.appcard", "Hyundai Card"),
        NotificationSourceApp("com.lotte.lottesmartpay", "Lotte Card"),
        NotificationSourceApp("com.samsung.android.spay", "Samsung Pay"),
        NotificationSourceApp("com.samsung.android.samsungpay.gear", "Samsung Pay (Gear)")
    )
    private val supportedPackageSet = supportedApps.map { it.packageName.trim().lowercase() }.toSet()

    fun supportedApps(): List<NotificationSourceApp> = supportedApps

    fun supportedPackageNames(): Set<String> = supportedPackageSet

    fun isSupported(
        context: Context,
        packageName: String,
        title: String? = null,
        text: String? = null
    ): Boolean {
        val normalized = packageName.trim().lowercase()
        if (normalized.isBlank()) {
            logDebug("Notification ignored: package name is blank")
            return false
        }
        if (normalized !in supportedPackageSet) {
            logDebug("Notification ignored: unsupported package [$normalized]")
            return false
        }

        val allowedPackages = NotificationSourcePrefs.loadAllowedPackages(context)
        if (allowedPackages.isEmpty()) {
            logDebug("Notification ignored: whitelist empty. package=$normalized")
            return false
        }
        if (normalized !in allowedPackages) {
            logDebug("Notification ignored: not selected package=$normalized")
            return false
        }

        val looksLikeTransaction = NotificationMessageParser.looksLikeTransaction(title, text)
        if (!looksLikeTransaction) {
            logDebug("Notification ignored: not transaction-like. package=$normalized")
            return false
        }

        return true
    }

    // Kept for non-service call-sites (e.g., test injection pre-check).
    fun isSupported(packageName: String, title: String? = null, text: String? = null): Boolean {
        val normalized = packageName.trim().lowercase()
        if (normalized !in supportedPackageSet) {
            logDebug("Notification test ignored: unsupported package [$normalized]")
            return false
        }

        val looksLikeTransaction = NotificationMessageParser.looksLikeTransaction(title, text)
        if (!looksLikeTransaction) {
            logDebug("Notification test ignored: not transaction-like. package=$normalized")
            return false
        }

        return true
    }

    private fun logDebug(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG, message)
        }
    }
}
