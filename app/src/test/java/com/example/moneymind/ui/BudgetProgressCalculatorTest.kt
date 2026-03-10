package com.example.moneymind.ui

import com.example.moneymind.domain.BudgetTarget
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.LedgerEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class BudgetProgressCalculatorTest {
    @Test
    fun calculate_buildsCategoryProgressAndOverrunMessages() {
        val month = YearMonth.of(2026, 2)
        val entries = listOf(
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 3, 12, 0),
                amount = 13_000L,
                type = EntryType.EXPENSE,
                category = "식비",
                description = "점심",
                merchant = "식당",
                source = EntrySource.MANUAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 5, 8, 30),
                amount = 7_000L,
                type = EntryType.EXPENSE,
                category = "교통",
                description = "택시",
                merchant = "카카오T",
                source = EntrySource.MANUAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 6, 9, 0),
                amount = 4_000L,
                type = EntryType.TRANSFER,
                category = "이체",
                description = "계좌이체",
                merchant = "토스",
                source = EntrySource.MANUAL,
                countedInExpense = false
            )
        )
        val targets = listOf(
            BudgetTarget(key = "TOTAL", category = null, amount = 15_000L),
            BudgetTarget(key = "CATEGORY:식비", category = "식비", amount = 10_000L),
            BudgetTarget(key = "CATEGORY:교통", category = "교통", amount = 9_000L)
        )

        val progress = BudgetProgressCalculator.calculate(entries, targets, month)

        assertEquals(20_000L, progress.totalExpense)
        assertEquals(-5_000L, progress.totalRemaining)
        assertEquals(2, progress.categoryProgress.size)
        assertEquals("식비", progress.categoryProgress.first().category)
        assertEquals(13_000L, progress.categoryProgress.first().used)
        assertEquals(-3_000L, progress.categoryProgress.first().remaining)
        assertTrue(progress.overBudgetMessages.contains("총예산 초과 5000원"))
        assertTrue(progress.overBudgetMessages.contains("식비 예산 초과 3000원"))
    }

    @Test
    fun calculate_ignoresOtherMonthsAndNonExpenseEntries() {
        val month = YearMonth.of(2026, 2)
        val entries = listOf(
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 1, 0, 0),
                amount = 5_000L,
                type = EntryType.EXPENSE,
                category = "문화",
                description = "영화",
                merchant = "CGV",
                source = EntrySource.MANUAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 3, 1, 0, 0),
                amount = 20_000L,
                type = EntryType.EXPENSE,
                category = "문화",
                description = "공연",
                merchant = "예매",
                source = EntrySource.MANUAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 2, 0, 0),
                amount = 9_000L,
                type = EntryType.INCOME,
                category = "수입",
                description = "용돈",
                merchant = "가족",
                source = EntrySource.MANUAL,
                countedInExpense = false
            )
        )
        val targets = listOf(
            BudgetTarget(key = "CATEGORY:문화", category = "문화", amount = 10_000L)
        )

        val progress = BudgetProgressCalculator.calculate(entries, targets, month)

        assertEquals(5_000L, progress.totalExpense)
        assertEquals(1, progress.categoryProgress.size)
        assertEquals(5_000L, progress.categoryProgress.single().used)
        assertEquals(5_000L, progress.categoryProgress.single().remaining)
        assertTrue(progress.overBudgetMessages.isEmpty())
    }
}
