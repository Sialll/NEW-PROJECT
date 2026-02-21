package com.example.moneymind.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClassificationEngineTest {
    private val engine = ClassificationEngine(InternalTransferDetector())

    @Test
    fun installmentFractionInRawColumn_marksInstallment() {
        val record = ParsedRecord(
            occurredAt = LocalDateTime.of(2026, 2, 11, 0, 0),
            signedAmount = -536_800L,
            description = "(주)이니시스 - 애플코리아 유한회사 1,610,000",
            merchant = "(주)이니시스 - 애플코리아 유한회사 1,610,000",
            source = EntrySource.EXCEL_IMPORT,
            raw = mapOf("할부/회차" to "3/1")
        )

        val entry = engine.classifyRecords(
            records = listOf(record),
            existing = emptyList(),
            ownedAccounts = emptyList(),
            ownerAliases = emptySet()
        ).single()

        assertEquals(SpendingKind.INSTALLMENT, entry.spendingKind)
        assertEquals("할부", entry.category)
    }

    @Test
    fun oneTimeFraction_staysNormal() {
        val record = ParsedRecord(
            occurredAt = LocalDateTime.of(2026, 2, 14, 0, 0),
            signedAmount = -10_000L,
            description = "일시불 결제",
            merchant = "카카오",
            source = EntrySource.EXCEL_IMPORT,
            raw = mapOf("할부/회차" to "1/1")
        )

        val entry = engine.classifyRecords(
            records = listOf(record),
            existing = emptyList(),
            ownedAccounts = emptyList(),
            ownerAliases = emptySet()
        ).single()

        assertEquals(SpendingKind.NORMAL, entry.spendingKind)
    }

    @Test
    fun forcedExpenseRule_overridesTransferOrIncomeType() {
        val record = ParsedRecord(
            occurredAt = LocalDateTime.of(2026, 2, 21, 10, 0),
            signedAmount = 25_000L,
            description = "토스 내 계좌 이체",
            merchant = "토스",
            source = EntrySource.EXCEL_IMPORT
        )

        val rule = ClassificationRule(
            keyword = "토스 내 계좌 이체",
            spendingKind = SpendingKind.NORMAL,
            category = "일반지출",
            forcedType = EntryType.EXPENSE
        )

        val entry = engine.classifyRecords(
            records = listOf(record),
            existing = emptyList(),
            ownedAccounts = emptyList(),
            ownerAliases = emptySet(),
            classificationRules = listOf(rule)
        ).single()

        assertEquals(EntryType.EXPENSE, entry.type)
        assertTrue(entry.countedInExpense)
        assertEquals("일반지출", entry.category)
    }

    @Test
    fun applyRuleIfMatched_changesExistingTransferToExpense() {
        val entry = LedgerEntry(
            occurredAt = LocalDateTime.of(2026, 2, 21, 9, 0),
            amount = 30_000L,
            type = EntryType.TRANSFER,
            category = "이체",
            description = "토스 내 계좌 이체",
            merchant = "토스",
            source = EntrySource.EXCEL_IMPORT,
            spendingKind = SpendingKind.NORMAL,
            countedInExpense = false
        )
        val rule = ClassificationRule(
            keyword = "토스 내 계좌 이체",
            spendingKind = SpendingKind.NORMAL,
            category = "일반지출",
            forcedType = EntryType.EXPENSE
        )

        val updated = engine.applyRuleIfMatched(entry, listOf(rule))

        assertEquals(EntryType.EXPENSE, updated.type)
        assertEquals("일반지출", updated.category)
        assertTrue(updated.countedInExpense)
    }

    @Test
    fun classifyRecords_reusesCategoryFromRecentHistory_whenMerchantMatches() {
        val existing = listOf(
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 19, 9, 0),
                amount = 12_000L,
                type = EntryType.EXPENSE,
                category = "생활기타",
                description = "MCS 정기 결제",
                merchant = "MCS-PAY",
                source = EntrySource.EXCEL_IMPORT
            )
        )
        val record = ParsedRecord(
            occurredAt = LocalDateTime.of(2026, 2, 21, 9, 0),
            signedAmount = -15_000L,
            description = "MCS 결제",
            merchant = "MCS-PAY",
            source = EntrySource.EXCEL_IMPORT
        )

        val entry = engine.classifyRecords(
            records = listOf(record),
            existing = existing,
            ownedAccounts = emptyList(),
            ownerAliases = emptySet(),
            classificationRules = emptyList()
        ).single()

        assertEquals("생활기타", entry.category)
    }
}
