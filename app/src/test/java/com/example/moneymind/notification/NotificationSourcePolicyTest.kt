package com.example.moneymind.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSourcePolicyTest {
    @Test
    fun isSupported_nonContext_rejectsUnsupportedPackage() {
        val supportedTransaction = "카드 결제 12,340원"
        val unsupportedResult = NotificationSourcePolicy.isSupported(
            packageName = "com.android.chrome",
            title = "브라우저 알림",
            text = supportedTransaction
        )
        assertFalse("Unsupported package should be filtered", unsupportedResult)
    }

    @Test
    fun isSupported_nonContext_acceptsSupportedBankPackage() {
        val result = NotificationSourcePolicy.isSupported(
            packageName = "viva.republica.toss",
            title = "토스",
            text = "오늘 15,000원 결제 승인"
        )
        assertTrue(result)
    }

    @Test
    fun isSupported_nonContext_rejectsUnsupportedTitleFormat() {
        val result = NotificationSourcePolicy.isSupported(
            packageName = "viva.republica.toss",
            title = "혜택 알림",
            text = "출석 이벤트에 참여하면 포인트 적립"
        )
        assertFalse(result)
    }
}
