package com.example.moneymind.ui

import com.example.moneymind.domain.BudgetTarget
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.LedgerEntry
import java.time.YearMonth
import kotlin.math.abs

object BudgetProgressCalculator {
    fun calculate(
        entries: List<LedgerEntry>,
        targets: List<BudgetTarget>,
        month: YearMonth
    ): BudgetProgress {
        val expenses = entries.filter {
            it.type == EntryType.EXPENSE &&
                it.countedInExpense &&
                YearMonth.from(it.occurredAt) == month
        }

        val totalExpense = expenses.sumOf { it.amount }
        val totalBudget = targets.firstOrNull { it.category == null && it.amount > 0L }?.amount
        val totalRemaining = totalBudget?.minus(totalExpense)

        val expenseByCategory = expenses
            .groupBy { it.category.trim().lowercase() }
            .mapValues { (_, values) -> values.sumOf { it.amount } }

        val categoryProgress = targets
            .asSequence()
            .filter { it.amount > 0L }
            .mapNotNull { target ->
                val category = target.category?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val used = expenseByCategory[category.lowercase()] ?: 0L
                CategoryBudgetProgress(
                    category = category,
                    budget = target.amount,
                    used = used,
                    remaining = target.amount - used
                )
            }
            .sortedWith(compareBy<CategoryBudgetProgress> { it.remaining >= 0L }.thenBy { it.remaining }.thenBy { it.category })
            .toList()

        val overMessages = buildList {
            if (totalRemaining != null && totalRemaining < 0L) {
                add("총예산 초과 ${abs(totalRemaining)}원")
            }
            categoryProgress
                .filter { it.remaining < 0L }
                .forEach { progress ->
                    add("${progress.category} 예산 초과 ${abs(progress.remaining)}원")
                }
        }

        return BudgetProgress(
            totalBudget = totalBudget,
            totalExpense = totalExpense,
            totalRemaining = totalRemaining,
            categoryProgress = categoryProgress,
            overBudgetMessages = overMessages
        )
    }
}
