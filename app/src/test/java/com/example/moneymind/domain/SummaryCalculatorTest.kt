package com.example.moneymind.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class SummaryCalculatorTest {
    @Test
    fun summarize_onlyTargetsRequestedMonth() {
        val targetMonth = YearMonth.of(2026, 2)
        val entries = listOf(
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 10, 12, 0),
                amount = 2_000_000L,
                type = EntryType.INCOME,
                category = "Income",
                description = "Salary",
                merchant = null,
                source = EntrySource.MANUAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 11, 9, 0),
                amount = 35_000L,
                type = EntryType.EXPENSE,
                category = "Food",
                description = "Lunch",
                merchant = null,
                source = EntrySource.MANUAL,
                spendingKind = SpendingKind.NORMAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 12, 10, 0),
                amount = 20_000L,
                type = EntryType.EXPENSE,
                category = "Subscription",
                description = "Streaming",
                merchant = null,
                source = EntrySource.MANUAL,
                spendingKind = SpendingKind.SUBSCRIPTION
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 13, 11, 0),
                amount = 45_000L,
                type = EntryType.EXPENSE,
                category = "Installment",
                description = "Laptop installment",
                merchant = null,
                source = EntrySource.MANUAL,
                spendingKind = SpendingKind.INSTALLMENT
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 2, 14, 14, 0),
                amount = 22_000L,
                type = EntryType.EXPENSE,
                category = "Misc",
                description = "Loan interest payment",
                merchant = null,
                source = EntrySource.MANUAL,
                spendingKind = SpendingKind.NORMAL
            ),
            LedgerEntry(
                occurredAt = LocalDateTime.of(2026, 1, 31, 12, 0),
                amount = 99_000L,
                type = EntryType.INCOME,
                category = "Income",
                description = "Previous income",
                merchant = null,
                source = EntrySource.MANUAL
            )
        )

        val summary = SummaryCalculator.summarize(entries, targetMonth)

        assertEquals(2_000_000L, summary.income)
        assertEquals(122_000L, summary.expense)
        assertEquals(0L, summary.transfer)
        assertEquals(20_000L, summary.subscriptionExpense)
        assertEquals(45_000L, summary.installmentExpense)
        assertEquals(22_000L, summary.loanExpense)
    }
}
