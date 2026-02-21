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
        val categoryHistory = buildCategoryHistory(existing).toMutableMap()

        val firstPass = records.map { record ->
            val isTransfer = internalTransferDetector.isInternalTransfer(record, ownedAccounts, ownerAliases)
            val initialType = when {
                isTransfer -> EntryType.TRANSFER
                record.signedAmount < 0L -> EntryType.EXPENSE
                else -> EntryType.INCOME
            }
            val matchedRule = findMatchedRule(record.description, record.merchant, enabledRules)
            val type = matchedRule?.forcedType ?: initialType

            val detectedKind = if (type == EntryType.EXPENSE) {
                detectSpendingKind(record.description, record.merchant, record.raw)
            } else {
                SpendingKind.NORMAL
            }

            val spendingKind = if (type == EntryType.EXPENSE) {
                matchedRule?.spendingKind ?: detectedKind
            } else {
                SpendingKind.NORMAL
            }
            val transferCategory = if (isTransfer) "\uB0B4\uBD80\uACC4\uC88C\uC774\uCCB4" else "\uC774\uCCB4"
            val recommendedCategory = if (type == EntryType.EXPENSE && matchedRule == null) {
                findRecommendedCategory(record.description, record.merchant, categoryHistory)
            } else {
                null
            }

            val category = when {
                matchedRule != null && matchedRule.category.isNotBlank() -> matchedRule.category
                type == EntryType.TRANSFER -> transferCategory
                type == EntryType.INCOME -> detectIncomeCategory(record.description)
                spendingKind == SpendingKind.LOAN -> "\uB300\uCD9C\uC0C1\uD658"
                spendingKind == SpendingKind.INSTALLMENT -> "\uD560\uBD80"
                !recommendedCategory.isNullOrBlank() -> recommendedCategory
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
                countedInExpense = type == EntryType.EXPENSE,
                accountMask = record.accountMask,
                counterpartyName = record.counterpartyName
            ).also { entry ->
                if (entry.type == EntryType.EXPENSE && entry.countedInExpense) {
                    categoryHistoryKeys(entry.description, entry.merchant).forEach { key ->
                        categoryHistory[key] = entry.category
                    }
                }
            }
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
        if (rules.isEmpty()) return entry

        val matchedRule = findMatchedRule(entry.description, entry.merchant, rules)
            ?: return entry

        val resolvedType = matchedRule.forcedType ?: entry.type
        val resolvedKind = if (resolvedType == EntryType.EXPENSE) {
            matchedRule.spendingKind
        } else {
            SpendingKind.NORMAL
        }

        return entry.copy(
            type = resolvedType,
            spendingKind = resolvedKind,
            category = matchedRule.category.ifBlank { defaultCategoryFor(resolvedKind, resolvedType) },
            countedInExpense = resolvedType == EntryType.EXPENSE
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

    private fun detectSpendingKind(
        description: String,
        merchant: String?,
        raw: Map<String, String>
    ): SpendingKind {
        val normalizedRaw = raw.entries.associate { (key, value) ->
            normalizeRawKey(key) to value.trim()
        }
        val installmentHint = installmentHintKeys.firstNotNullOfOrNull { key -> normalizedRaw[key] }
            .orEmpty()
        val merged = "${description.lowercase()} ${merchant.orEmpty().lowercase()} ${installmentHint.lowercase()}"

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
            val first = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@let
            val second = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@let
            if (first in 1..99 && second in 1..99 && (first >= 2 || second >= 2)) {
                return SpendingKind.INSTALLMENT
            }
        }

        if (installmentMonthsPattern.containsMatchIn(merged)) {
            return SpendingKind.INSTALLMENT
        }

        return SpendingKind.NORMAL
    }

    private fun normalizeRawKey(value: String): String {
        return value.trim().lowercase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace("/", "")
            .replace(".", "")
            .replace(":", "")
            .replace("(", "")
            .replace(")", "")
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

    private fun buildCategoryHistory(entries: List<LedgerEntry>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        entries
            .asSequence()
            .filter { it.type == EntryType.EXPENSE && it.countedInExpense }
            .filter { it.category.isNotBlank() }
            .sortedByDescending { it.occurredAt }
            .forEach { entry ->
                categoryHistoryKeys(entry.description, entry.merchant).forEach { key ->
                    map.putIfAbsent(key, entry.category)
                }
            }
        return map
    }

    private fun findRecommendedCategory(
        description: String,
        merchant: String?,
        history: Map<String, String>
    ): String? {
        return categoryHistoryKeys(description, merchant)
            .firstNotNullOfOrNull { key -> history[key] }
    }

    private fun categoryHistoryKeys(description: String, merchant: String?): List<String> {
        val merchantKey = normalizeMerchantToken(merchant.orEmpty())
        val descriptionKey = normalizeDescriptionToken(description)
        return buildList {
            if (merchantKey.length >= 2) {
                add("merchant:$merchantKey")
            }
            if (descriptionKey.length >= 4) {
                add("description:$descriptionKey")
            }
            if (merchantKey.isNotBlank() && descriptionKey.isNotBlank()) {
                add("pair:$merchantKey|$descriptionKey")
            }
        }
    }

    private fun normalizeMerchantToken(value: String): String {
        return value.lowercase()
            .replace(Regex("[^0-9a-z가-힣]"), "")
            .take(24)
    }

    private fun normalizeDescriptionToken(value: String): String {
        return value.lowercase()
            .replace(Regex("[^0-9a-z가-힣]"), "")
            .take(24)
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

    companion object {
        private val installmentHintKeys = listOf(
            "할부/회차",
            "할부회차",
            "할부",
            "회차",
            "개월",
            "분할"
        ).map { key ->
            key.trim().lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("/", "")
                .replace(".", "")
                .replace(":", "")
                .replace("(", "")
                .replace(")", "")
        }
    }
}
