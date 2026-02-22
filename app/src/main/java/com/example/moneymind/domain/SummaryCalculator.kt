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
        var income = 0L
        var expense = 0L
        var transfer = 0L
        var subscription = 0L
        var installment = 0L
        var loan = 0L

        for (entry in entries) {
            if (YearMonth.from(entry.occurredAt) != month) {
                continue
            }

            when (entry.type) {
                EntryType.INCOME -> {
                    income += entry.amount
                }
                EntryType.TRANSFER -> {
                    transfer += entry.amount
                }
                EntryType.EXPENSE -> {
                    if (!entry.countedInExpense) continue
                    expense += entry.amount

                    when (entry.spendingKind) {
                        SpendingKind.SUBSCRIPTION -> subscription += entry.amount
                        SpendingKind.INSTALLMENT -> installment += entry.amount
                        else -> { /* no-op */ }
                    }

                    if (isLoanExpense(entry)) {
                        loan += entry.amount
                    }
                }
            }
        }

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
