package com.example.moneymind.notification

object NotificationSourcePolicy {
    private val allowPackages = setOf(
        "com.kakaobank.channel",
        "viva.republica.toss",
        "kr.co.kfcc.mobile",
        "com.kbstar.kbbank",
        "com.shinhan.sbanking",
        "com.hanabank.ebk.channel.android.hananbank",
        "com.kbankwith.smartbank",
        "com.wooribank.smart.npib",
        "com.ibk.neobanking",
        "com.kbcard.cxh.appcard",
        "com.shcard.smartpay",
        "com.samsung.android.spay",
        "com.samsung.android.samsungpay.gear"
    )

    private val allowPackagePrefixes = listOf(
        "com.kbstar.",
        "com.shinhan.",
        "com.hanabank.",
        "com.wooribank.",
        "com.ibk.",
        "com.kbcard.",
        "com.shcard.",
        "com.hyundaicard.",
        "com.lottecard.",
        "com.samsungcard.",
        "com.kakaobank.",
        "com.kakao.",
        "com.nh.",
        "viva.republica."
    )

    fun isSupported(packageName: String, title: String? = null, text: String? = null): Boolean {
        val normalized = packageName.trim().lowercase()
        if (normalized.isBlank()) return false
        if (normalized in allowPackages) return true
        if (allowPackagePrefixes.any { normalized.startsWith(it) }) return true

        // Fallback: unknown package can still be ingested when the notification clearly
        // looks like a transaction message.
        return NotificationMessageParser.looksLikeTransaction(title, text)
    }
}
