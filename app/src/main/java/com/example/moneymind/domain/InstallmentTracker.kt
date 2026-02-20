package com.example.moneymind.domain

import java.time.YearMonth

class InstallmentTracker {
    private val plans = mutableListOf<InstallmentPlan>()

    fun register(plan: InstallmentPlan) {
        plans.removeAll { it.id == plan.id }
        plans.add(plan)
    }

    fun allPlans(): List<InstallmentPlan> = plans.toList()

    fun projectedWarnings(month: YearMonth): List<InstallmentWarning> {
        return plans
            .filter { it.isActive(month) }
            .map { plan ->
                InstallmentWarning(
                    message = "${month.monthValue}월 할부 결제 예정",
                    amount = plan.monthlyAmount,
                    merchant = plan.merchant,
                    remainingMonths = plan.monthsRemaining(month)
                )
            }
            .sortedByDescending { it.amount }
    }
}
