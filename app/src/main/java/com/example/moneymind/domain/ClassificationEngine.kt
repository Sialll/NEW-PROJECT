package com.example.moneymind.domain

import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

class ClassificationEngine(
    private val internalTransferDetector: InternalTransferDetector
) {
    private val incomeKeywords = listOf(
        "\uAE09\uC5EC",
        "\uC6D4\uAE09",
        "\uC0C1\uC5EC",
        "\uD658\uAE09",
        "\uC785\uAE08",
        "salary",
        "deposit",
        "refund"
    )

    private val expenseCategoryRules = listOf(
        "\uC2DD\uBE44" to listOf("\uC2DD\uB2F9", "\uCE74\uD398", "\uBC30\uB2EC", "\uD3B8\uC758\uC810", "\uB808\uC2A4\uD1A0\uB791"),
        "\uAD50\uD1B5" to listOf("\uC9C0\uD558\uCCA0", "\uBC84\uC2A4", "\uD0DD\uC2DC", "\uC8FC\uC720", "\uD1B5\uD589\uB8CC"),
        "\uC1FC\uD551" to listOf("\uCFE0\uD321", "\uB124\uC774\uBC84", "\uB9C8\uCF13", "\uC2A4\uD1A0\uC5B4", "shopping"),
        "\uD1B5\uC2E0/\uAD6C\uB3C5" to listOf("\uD1B5\uC2E0", "netflix", "youtube", "spotify", "\uAD6C\uB3C5", "\uBA64\uBC84\uC2ED"),
        "\uC8FC\uAC70/\uACF5\uACFC\uAE08" to listOf("\uAD00\uB9AC\uBE44", "\uC804\uAE30", "\uAC00\uC2A4", "\uC218\uB3C4", "\uC6D4\uC138"),
        "\uAE08\uC735" to listOf("\uC218\uC218\uB8CC", "\uC774\uC790", "\uBCF4\uD5D8")
    )

    private val installmentKeywords = listOf(
        "\uD560\uBD80",
        "\uBD80\uBD84\uBB34\uC774\uC790",
        "\uBB34\uC774\uC790",
        "\uAC1C\uC6D4"
    )

    private val loanKeywords = listOf(
        "\uB300\uCD9C",
        "\uC6D0\uB9AC\uAE08",
        "\uC774\uC790",
        "\uCE74\uB4DC\uB860",
        "\uD604\uAE08\uC11C\uBE44\uC2A4",
        "\uB9AC\uBCFC\uBE59",
        "loan",
        "mortgage",
        "interest"
    )

    private val oneTimePaymentKeywords = listOf(
        "\uC77C\uC2DC\uBD88",
        "single payment"
    )

    private val installmentFractionPattern = Regex("(\\d{1,2})\\s*/\\s*(\\d{1,2})")
    private val installmentMonthsPattern = Regex("(\\d{1,2})\\s*(\uAC1C\uC6D4|\uD560\uBD80)")

    fun classifyRecords(
        records: List<ParsedRecord>,
        existing: List<LedgerEntry>,
        ownedAccounts: List<OwnedAccount>,
        ownerAliases: Set<String>,
        classificationRules: List<ClassificationRule> = emptyList()
    ): List<LedgerEntry> {
        val enabledRules = classificationRules
            .filter { it.enabled && it.keyword.isNotBlank() }
            .sortedByDescending { it.keyword.length }

        val firstPass = records.map { record ->
            val isTransfer = internalTransferDetector.isInternalTransfer(record, ownedAccounts, ownerAliases)
            val type = when {
                isTransfer -> EntryType.TRANSFER
                record.signedAmount < 0L -> EntryType.EXPENSE
                else -> EntryType.INCOME
            }

            val detectedKind = if (type == EntryType.EXPENSE) {
                detectSpendingKind(record.description, record.merchant)
            } else {
                SpendingKind.NORMAL
            }

            val matchedRule = if (type == EntryType.EXPENSE) {
                findMatchedRule(record.description, record.merchant, enabledRules)
            } else {
                null
            }
            val spendingKind = matchedRule?.spendingKind ?: detectedKind

            val category = when {
                isTransfer -> "\uB0B4\uBD80\uACC4\uC88C\uC774\uCCB4"
                type == EntryType.INCOME -> detectIncomeCategory(record.description)
                matchedRule != null -> matchedRule.category.ifBlank { defaultCategoryFor(spendingKind, type) }
                spendingKind == SpendingKind.LOAN -> "\uB300\uCD9C\uC0C1\uD658"
                spendingKind == SpendingKind.INSTALLMENT -> "\uD560\uBD80"
                else -> detectExpenseCategory(record.description)
            }

            LedgerEntry(
                occurredAt = record.occurredAt,
                amount = record.signedAmount.absoluteValue,
                type = type,
                category = category,
                description = record.description,
                merchant = record.merchant,
                source = record.source,
                spendingKind = spendingKind,
                countedInExpense = type == EntryType.EXPENSE && !isTransfer,
                accountMask = record.accountMask,
                counterpartyName = record.counterpartyName
            )
        }

        val merged = (existing + firstPass).sortedBy { it.occurredAt }
        val subscriptionKeys = detectSubscriptions(merged)

        return firstPass.map { entry ->
            if (
                entry.type == EntryType.EXPENSE &&
                entry.spendingKind == SpendingKind.NORMAL &&
                subscriptionKeys.contains(subscriptionKey(entry))
            ) {
                entry.copy(
                    spendingKind = SpendingKind.SUBSCRIPTION,
                    category = "\uAD6C\uB3C5"
                )
            } else {
                entry
            }
        }
    }

    fun applyRuleIfMatched(entry: LedgerEntry, rules: List<ClassificationRule>): LedgerEntry {
        if (entry.type != EntryType.EXPENSE || rules.isEmpty()) return entry

        val matchedRule = findMatchedRule(entry.description, entry.merchant, rules)
            ?: return entry

        return entry.copy(
            spendingKind = matchedRule.spendingKind,
            category = matchedRule.category.ifBlank { defaultCategoryFor(matchedRule.spendingKind, entry.type) }
        )
    }

    private fun detectIncomeCategory(description: String): String {
        val lower = description.lowercase()
        return if (incomeKeywords.any { lower.contains(it.lowercase()) }) {
            "\uAE09\uC5EC/\uC785\uAE08"
        } else {
            "\uAE30\uD0C0\uC218\uC785"
        }
    }

    private fun detectExpenseCategory(description: String): String {
        val lower = description.lowercase()
        for ((category, keywords) in expenseCategoryRules) {
            if (keywords.any { lower.contains(it.lowercase()) }) {
                return category
            }
        }
        return "\uAE30\uD0C0\uC9C0\uCD9C"
    }

    private fun detectSpendingKind(description: String, merchant: String?): SpendingKind {
        val merged = "${description.lowercase()} ${merchant.orEmpty().lowercase()}"

        if (loanKeywords.any { merged.contains(it.lowercase()) }) {
            return SpendingKind.LOAN
        }

        if (oneTimePaymentKeywords.any { merged.contains(it.lowercase()) }) {
            return SpendingKind.NORMAL
        }

        if (installmentKeywords.any { merged.contains(it.lowercase()) }) {
            return SpendingKind.INSTALLMENT
        }

        installmentFractionPattern.find(merged)?.let { match ->
            val current = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            val total = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
            if (current in 1..99 && total in 2..99 && current <= total) {
                return SpendingKind.INSTALLMENT
            }
        }

        if (installmentMonthsPattern.containsMatchIn(merged)) {
            return SpendingKind.INSTALLMENT
        }

        return SpendingKind.NORMAL
    }

    private fun findMatchedRule(
        description: String,
        merchant: String?,
        rules: List<ClassificationRule>
    ): ClassificationRule? {
        if (rules.isEmpty()) return null
        val merged = "${description.lowercase()} ${merchant.orEmpty().lowercase()}"
        return rules.firstOrNull { rule ->
            val keyword = rule.keyword.trim().lowercase()
            keyword.isNotBlank() && merged.contains(keyword)
        }
    }

    private fun defaultCategoryFor(kind: SpendingKind, type: EntryType): String {
        return when {
            type == EntryType.INCOME -> "\uAE09\uC5EC/\uC785\uAE08"
            kind == SpendingKind.SUBSCRIPTION -> "\uAD6C\uB3C5"
            kind == SpendingKind.INSTALLMENT -> "\uD560\uBD80"
            kind == SpendingKind.LOAN -> "\uB300\uCD9C\uC0C1\uD658"
            type == EntryType.TRANSFER -> "\uB0B4\uBD80\uACC4\uC88C\uC774\uCCB4"
            else -> "\uAE30\uD0C0\uC9C0\uCD9C"
        }
    }

    private fun subscriptionKey(entry: LedgerEntry): String {
        val merchantOrDesc = entry.merchant
            ?.trim()
            ?.ifBlank { entry.description }
            ?: entry.description
        val normalizedMerchant = merchantOrDesc.lowercase().replace(Regex("\\s+"), "")
        return "$normalizedMerchant:${entry.amount}"
    }

    private fun detectSubscriptions(entries: List<LedgerEntry>): Set<String> {
        val expenseCandidates = entries.filter {
            it.type == EntryType.EXPENSE &&
                it.countedInExpense &&
                it.spendingKind == SpendingKind.NORMAL
        }

        val grouped = expenseCandidates.groupBy { subscriptionKey(it) }
        val result = mutableSetOf<String>()

        grouped.forEach { (key, values) ->
            val sorted = values.sortedBy { it.occurredAt }
            if (sorted.size < 3) return@forEach

            val dayGaps = sorted.zipWithNext { a, b ->
                ChronoUnit.DAYS.between(a.occurredAt.toLocalDate(), b.occurredAt.toLocalDate())
            }
            val monthlyPattern = dayGaps.all { it in 25..40 }
            if (monthlyPattern) {
                result.add(key)
            }
        }
        return result
    }
}
