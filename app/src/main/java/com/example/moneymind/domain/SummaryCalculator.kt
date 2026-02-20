package com.example.moneymind.domain

import java.time.YearMonth

object SummaryCalculator {
    private val loanKeywords = listOf(
        "\uB300\uCD9C",
        "\uC6D0\uB9AC\uAE08",
        "\uC774\uC790",
        "\uCE74\uB4DC\uB860",
        "\uC2E0\uC6A9\uB300\uCD9C",
        "\uB9C8\uC774\uB108\uC2A4\uD1B5\uC7A5",
        "loan",
        "mortgage",
        "interest"
    )

    fun summarize(entries: List<LedgerEntry>, month: YearMonth): MonthlySummary {
        val monthly = entries.filter { YearMonth.from(it.occurredAt) == month }
        val income = monthly.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
        val expense = monthly.filter { it.type == EntryType.EXPENSE && it.countedInExpense }.sumOf { it.amount }
        val transfer = monthly.filter { it.type == EntryType.TRANSFER }.sumOf { it.amount }
        val subscription = monthly
            .filter {
                it.type == EntryType.EXPENSE &&
                    it.spendingKind == SpendingKind.SUBSCRIPTION &&
                    it.countedInExpense
            }
            .sumOf { it.amount }
        val installment = monthly
            .filter {
                it.type == EntryType.EXPENSE &&
                    it.spendingKind == SpendingKind.INSTALLMENT &&
                    it.countedInExpense
            }
            .sumOf { it.amount }
        val loan = monthly
            .filter { it.type == EntryType.EXPENSE && it.countedInExpense && isLoanExpense(it) }
            .sumOf { it.amount }

        return MonthlySummary(
            month = month,
            income = income,
            expense = expense,
            transfer = transfer,
            subscriptionExpense = subscription,
            installmentExpense = installment,
            loanExpense = loan
        )
    }

    private fun isLoanExpense(entry: LedgerEntry): Boolean {
        if (entry.spendingKind == SpendingKind.LOAN) return true
        if (entry.spendingKind == SpendingKind.SUBSCRIPTION) return false

        val combined = buildString {
            append(entry.category.lowercase())
            append(' ')
            append(entry.description.lowercase())
            append(' ')
            append(entry.merchant?.lowercase().orEmpty())
        }
        return loanKeywords.any { combined.contains(it.lowercase()) }
    }
}
