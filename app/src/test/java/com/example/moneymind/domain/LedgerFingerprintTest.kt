package com.example.moneymind.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime

class LedgerFingerprintTest {
    @Test
    fun sameMinuteDifferentAccountMasks_doNotCollide() {
        val occurredAt = LocalDateTime.of(2026, 2, 21, 7, 10, 12)
        val first = LedgerEntry(
            occurredAt = occurredAt,
            amount = 15_000L,
            type = EntryType.EXPENSE,
            category = "식비",
            description = "편의점 결제",
            merchant = "GS25",
            source = EntrySource.CSV_IMPORT,
            accountMask = "1234"
        )
        val second = first.copy(accountMask = "9876")

        assertNotEquals(LedgerFingerprint.build(first), LedgerFingerprint.build(second))
    }

    @Test
    fun sameMinuteDifferentMerchants_doNotCollide() {
        val occurredAt = LocalDateTime.of(2026, 2, 21, 7, 10, 12)
        val first = LedgerEntry(
            occurredAt = occurredAt,
            amount = 15_000L,
            type = EntryType.EXPENSE,
            category = "식비",
            description = "카드 승인",
            merchant = "STARBUCKS",
            source = EntrySource.CSV_IMPORT
        )
        val second = first.copy(merchant = "MEGA COFFEE")

        assertNotEquals(LedgerFingerprint.build(first), LedgerFingerprint.build(second))
    }

    @Test
    fun identicalRawEntry_keepsStableFingerprint() {
        val entry = LedgerEntry(
            occurredAt = LocalDateTime.of(2026, 2, 21, 7, 10, 12),
            amount = 15_000L,
            type = EntryType.EXPENSE,
            category = "식비",
            description = "편의점 결제",
            merchant = "GS25",
            source = EntrySource.CSV_IMPORT,
            accountMask = "1234",
            counterpartyName = "홍길동"
        )

        assertEquals(LedgerFingerprint.build(entry), LedgerFingerprint.build(entry.copy()))
    }
}
