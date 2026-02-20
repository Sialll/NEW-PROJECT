package com.example.moneymind.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

enum class EntryType {
    INCOME,
    EXPENSE,
    TRANSFER
}

enum class EntrySource {
    NOTIFICATION,
    CSV_IMPORT,
    EXCEL_IMPORT,
    PDF_IMPORT,
    MANUAL
}

enum class SpendingKind {
    NORMAL,
    SUBSCRIPTION,
    INSTALLMENT,
    LOAN
}

data class ParsedRecord(
    val occurredAt: LocalDateTime,
    val signedAmount: Long,
    val description: String,
    val merchant: String? = null,
    val accountMask: String? = null,
    val fromAccountMask: String? = null,
    val toAccountMask: String? = null,
    val counterpartyName: String? = null,
    val source: EntrySource,
    val raw: Map<String, String> = emptyMap()
)

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val occurredAt: LocalDateTime,
    val amount: Long,
    val type: EntryType,
    val category: String,
    val description: String,
    val merchant: String?,
    val source: EntrySource,
    val spendingKind: SpendingKind = SpendingKind.NORMAL,
    val countedInExpense: Boolean = type == EntryType.EXPENSE,
    val accountMask: String? = null,
    val counterpartyName: String? = null
)

data class OwnedAccount(
    val bank: String,
    val accountMask: String,
    val ownerName: String
)

data class CardInfo(
    val cardAlias: String,
    val last4: String
)

data class InstallmentPlan(
    val id: String = UUID.randomUUID().toString(),
    val cardLast4: String,
    val merchant: String,
    val monthlyAmount: Long,
    val totalMonths: Int,
    val startMonth: YearMonth
) {
    fun isActive(month: YearMonth): Boolean {
        val index = month.year * 12 + month.monthValue - (startMonth.year * 12 + startMonth.monthValue)
        return index in 0 until totalMonths
    }

    fun monthsRemaining(month: YearMonth): Int {
        val index = month.year * 12 + month.monthValue - (startMonth.year * 12 + startMonth.monthValue)
        return when {
            index < 0 -> totalMonths
            index >= totalMonths -> 0
            else -> totalMonths - index
        }
    }
}

data class MonthlySummary(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val transfer: Long,
    val subscriptionExpense: Long,
    val installmentExpense: Long,
    val loanExpense: Long
)

data class InstallmentWarning(
    val message: String,
    val amount: Long,
    val merchant: String,
    val remainingMonths: Int
)

data class QuickTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: EntryType,
    val amount: Long,
    val description: String,
    val merchant: String?,
    val category: String,
    val spendingKind: SpendingKind = SpendingKind.NORMAL,
    val repeatMonthlyDay: Int? = null,
    val enabled: Boolean = true
)

data class BudgetTarget(
    val key: String,
    val category: String?,
    val amount: Long
)

data class MonthlyClosing(
    val month: YearMonth,
    val carryIn: Long,
    val expectedClosing: Long,
    val actualClosing: Long?,
    val delta: Long?,
    val closedAtMillis: Long?
)

data class ClassificationRule(
    val id: String = UUID.randomUUID().toString(),
    val keyword: String,
    val spendingKind: SpendingKind,
    val category: String,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

fun YearMonth.toStartDateTime(): LocalDateTime = atDay(1).atStartOfDay()
fun LocalDate.toDateTime(): LocalDateTime = atStartOfDay()
