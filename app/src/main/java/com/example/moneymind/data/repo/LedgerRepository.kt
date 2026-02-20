package com.example.moneymind.data.repo

import com.example.moneymind.data.local.BudgetTargetEntity
import com.example.moneymind.data.local.ClassificationRuleEntity
import com.example.moneymind.data.local.InstallmentPlanEntity
import com.example.moneymind.data.local.LedgerEntryEntity
import com.example.moneymind.data.local.MonthlyClosingEntity
import com.example.moneymind.data.local.MoneyMindDatabase
import com.example.moneymind.data.local.OwnedAccountEntity
import com.example.moneymind.data.local.OwnerAliasEntity
import com.example.moneymind.data.local.QuickTemplateEntity
import com.example.moneymind.domain.BudgetTarget
import com.example.moneymind.domain.ClassificationEngine
import com.example.moneymind.domain.ClassificationRule
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.InstallmentPlan
import com.example.moneymind.domain.InternalTransferDetector
import com.example.moneymind.domain.LedgerEntry
import com.example.moneymind.domain.MonthlyClosing
import com.example.moneymind.domain.OwnedAccount
import com.example.moneymind.domain.ParsedRecord
import com.example.moneymind.domain.QuickTemplate
import com.example.moneymind.domain.SpendingKind
import com.example.moneymind.security.SecureTextCipher
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepository(
    private val database: MoneyMindDatabase,
    private val secureTextCipher: SecureTextCipher? = null
) {
    private val classifier = ClassificationEngine(InternalTransferDetector())
    private val zoneId = ZoneId.systemDefault()

    val entriesFlow: Flow<List<LedgerEntry>> =
        database.ledgerDao().observeAll().map { list -> list.map { it.toDomain() } }

    val ownedAccountsFlow: Flow<List<OwnedAccount>> =
        database.ownedAccountDao().observeAll().map { list -> list.map { it.toDomain() } }

    val ownerAliasesFlow: Flow<Set<String>> =
        database.ownerAliasDao().observeAll().map { list -> list.map { it.alias }.toSet() }

    val installmentPlansFlow: Flow<List<InstallmentPlan>> =
        database.installmentPlanDao().observeAll().map { list -> list.map { it.toDomain() } }

    val quickTemplatesFlow: Flow<List<QuickTemplate>> =
        database.quickTemplateDao().observeAll().map { list -> list.map { it.toDomain() } }

    val budgetTargetsFlow: Flow<List<BudgetTarget>> =
        database.budgetTargetDao().observeAll().map { list -> list.map { it.toDomain() } }

    val monthlyClosingsFlow: Flow<List<MonthlyClosing>> =
        database.monthlyClosingDao().observeAll().map { list -> list.map { it.toDomain() } }

    val classificationRulesFlow: Flow<List<ClassificationRule>> =
        database.classificationRuleDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addOwnerAlias(alias: String) {
        val normalized = alias.trim()
        if (normalized.isBlank()) return
        database.ownerAliasDao().upsert(OwnerAliasEntity(alias = normalized))
    }

    suspend fun registerOwnedAccount(account: OwnedAccount) {
        if (account.accountMask.isBlank() || account.ownerName.isBlank()) return
        database.ownedAccountDao().upsert(account.toEntity())
        addOwnerAlias(account.ownerName)
    }

    suspend fun registerInstallment(plan: InstallmentPlan) {
        database.installmentPlanDao().upsert(plan.toEntity())
    }

    suspend fun addQuickTemplate(template: QuickTemplate) {
        if (template.name.isBlank() || template.description.isBlank() || template.amount <= 0L) return

        val normalizedType = template.type
        val normalizedKind = if (normalizedType == EntryType.EXPENSE) template.spendingKind else SpendingKind.NORMAL
        val normalizedCategory = template.category.trim().ifBlank {
            defaultCategoryFor(type = normalizedType, spendingKind = normalizedKind)
        }
        val repeatDay = template.repeatMonthlyDay?.coerceIn(1, 31)

        database.quickTemplateDao().upsert(
            template.copy(
                type = normalizedType,
                spendingKind = normalizedKind,
                category = normalizedCategory,
                repeatMonthlyDay = repeatDay,
                enabled = true
            ).toEntity()
        )
    }

    suspend fun deleteQuickTemplate(templateId: String) {
        if (templateId.isBlank()) return
        database.quickTemplateDao().deleteById(templateId)
    }

    suspend fun runQuickTemplateNow(templateId: String, occurredAt: LocalDateTime = LocalDateTime.now()) {
        val template = database.quickTemplateDao().getById(templateId)?.toDomain() ?: return
        if (!template.enabled) return

        val entry = LedgerEntry(
            occurredAt = occurredAt,
            amount = template.amount,
            type = template.type,
            category = template.category,
            description = template.description,
            merchant = template.merchant,
            source = EntrySource.MANUAL,
            spendingKind = template.spendingKind,
            countedInExpense = template.type == EntryType.EXPENSE
        )
        addManualEntry(entry)
    }

    suspend fun materializeRecurringTemplates(month: YearMonth = YearMonth.now()) {
        val templates = database.quickTemplateDao().getAll().map { it.toDomain() }
            .filter { it.enabled && it.repeatMonthlyDay != null }
        if (templates.isEmpty()) return

        templates.forEach { template ->
            val day = template.repeatMonthlyDay?.coerceIn(1, month.lengthOfMonth()) ?: return@forEach
            val occurredAt = month.atDay(day).atStartOfDay()
            val entryId = "recurring-${template.id}-${month}-$day"
            val recurringFingerprint = "recurring|${template.id}|$month|$day|${template.type}|${template.amount}"

            val entry = LedgerEntry(
                id = entryId,
                occurredAt = occurredAt,
                amount = template.amount,
                type = template.type,
                category = template.category,
                description = template.description,
                merchant = template.merchant,
                source = EntrySource.MANUAL,
                spendingKind = if (template.type == EntryType.EXPENSE) template.spendingKind else SpendingKind.NORMAL,
                countedInExpense = template.type == EntryType.EXPENSE
            )

            database.ledgerDao().upsert(entry.toEntity(fingerprint = recurringFingerprint))
        }
    }

    suspend fun setTotalBudget(amount: Long) {
        if (amount <= 0L) {
            database.budgetTargetDao().deleteByKey(TOTAL_BUDGET_KEY)
            return
        }
        database.budgetTargetDao().upsert(
            BudgetTargetEntity(
                key = TOTAL_BUDGET_KEY,
                category = null,
                amount = amount
            )
        )
    }

    suspend fun setCategoryBudget(category: String, amount: Long) {
        val normalizedCategory = category.trim()
        if (normalizedCategory.isBlank()) return

        val key = categoryBudgetKey(normalizedCategory)
        if (amount <= 0L) {
            database.budgetTargetDao().deleteByKey(key)
            return
        }

        database.budgetTargetDao().upsert(
            BudgetTargetEntity(
                key = key,
                category = normalizedCategory,
                amount = amount
            )
        )
    }

    suspend fun removeCategoryBudget(category: String) {
        val normalizedCategory = category.trim()
        if (normalizedCategory.isBlank()) return
        database.budgetTargetDao().deleteByKey(categoryBudgetKey(normalizedCategory))
    }

    suspend fun upsertClassificationRule(keyword: String, spendingKind: SpendingKind, category: String) {
        val normalizedKeyword = keyword.trim().lowercase()
        if (normalizedKeyword.isBlank()) return

        val normalizedCategory = category.trim().ifBlank {
            defaultCategoryFor(EntryType.EXPENSE, spendingKind)
        }

        database.classificationRuleDao().upsert(
            ClassificationRuleEntity(
                id = normalizedKeyword,
                keyword = normalizedKeyword,
                spendingKind = spendingKind.name,
                category = normalizedCategory,
                enabled = true,
                createdAtMillis = System.currentTimeMillis()
            )
        )

        applyClassificationRulesToExistingEntries()
    }

    suspend fun deleteClassificationRule(ruleId: String) {
        if (ruleId.isBlank()) return
        database.classificationRuleDao().deleteById(ruleId)
    }

    suspend fun applyClassificationRulesToExistingEntries() {
        val rules = database.classificationRuleDao().getAll().map { it.toDomain() }
        if (rules.isEmpty()) return

        val entries = database.ledgerDao().getAll().map { it.toDomain() }
        entries.forEach { entry ->
            if (entry.type != EntryType.EXPENSE) return@forEach
            val updated = classifier.applyRuleIfMatched(entry, rules)
            if (updated.spendingKind == entry.spendingKind && updated.category == entry.category) {
                return@forEach
            }
            database.ledgerDao().updateEntryById(
                id = entry.id,
                fingerprint = entryFingerprint(updated),
                type = updated.type.name,
                amount = updated.amount,
                category = updated.category,
                description = encryptText(updated.description).orEmpty(),
                merchant = encryptText(updated.merchant),
                spendingKind = updated.spendingKind.name,
                countedInExpense = updated.type == EntryType.EXPENSE
            )
        }
    }

    suspend fun closeMonth(month: YearMonth, actualClosing: Long?) {
        val monthValue = month.toString()
        val existing = database.monthlyClosingDao().getByMonth(monthValue)
        val previous = database.monthlyClosingDao().getLatestBefore(monthValue)

        val carryIn = previous?.actualClosing
            ?: previous?.expectedClosing
            ?: existing?.carryIn
            ?: 0L

        val entries = database.ledgerDao().getAll().map { it.toDomain() }
        val income = entries
            .filter { it.type == EntryType.INCOME && YearMonth.from(it.occurredAt) == month }
            .sumOf { it.amount }
        val expense = entries
            .filter {
                it.type == EntryType.EXPENSE &&
                    it.countedInExpense &&
                    YearMonth.from(it.occurredAt) == month
            }
            .sumOf { it.amount }
        val expected = carryIn + income - expense

        val resolvedActual = actualClosing ?: existing?.actualClosing
        val delta = resolvedActual?.minus(expected)

        database.monthlyClosingDao().upsert(
            MonthlyClosingEntity(
                month = monthValue,
                carryIn = carryIn,
                expectedClosing = expected,
                actualClosing = resolvedActual,
                delta = delta,
                closedAtMillis = if (resolvedActual != null) System.currentTimeMillis() else existing?.closedAtMillis
            )
        )
    }

    suspend fun addManualEntry(entry: LedgerEntry) {
        if (entry.amount <= 0L || entry.description.isBlank()) return

        val normalizedType = when (entry.type) {
            EntryType.INCOME -> EntryType.INCOME
            EntryType.EXPENSE -> EntryType.EXPENSE
            EntryType.TRANSFER -> EntryType.TRANSFER
        }
        val normalized = entry.copy(
            type = normalizedType,
            source = EntrySource.MANUAL,
            description = entry.description.trim(),
            merchant = entry.merchant?.trim()?.takeIf { it.isNotEmpty() },
            spendingKind = if (normalizedType == EntryType.EXPENSE) entry.spendingKind else SpendingKind.NORMAL,
            countedInExpense = normalizedType == EntryType.EXPENSE
        )

        // Manual entries should always be insertable even when amount/description match within the same minute.
        val manualFingerprint = "${entryFingerprint(normalized)}|manual|${normalized.id.takeLast(8)}"
        database.ledgerDao().upsert(normalized.toEntity(fingerprint = manualFingerprint))
    }

    suspend fun updateEntry(
        entryId: String,
        type: EntryType,
        amount: Long,
        description: String,
        category: String,
        merchant: String?,
        spendingKind: SpendingKind
    ) {
        if (entryId.isBlank() || amount <= 0L || description.isBlank()) return

        val resolvedKind = if (type == EntryType.EXPENSE) spendingKind else SpendingKind.NORMAL
        val resolvedCategory = category.trim().ifBlank {
            defaultCategoryFor(type = type, spendingKind = resolvedKind)
        }
        val entryLike = LedgerEntry(
            id = entryId,
            occurredAt = LocalDateTime.now(),
            amount = amount,
            type = type,
            category = resolvedCategory,
            description = description.trim(),
            merchant = merchant?.trim()?.takeIf { it.isNotEmpty() },
            source = EntrySource.MANUAL,
            spendingKind = resolvedKind,
            countedInExpense = type == EntryType.EXPENSE
        )

        database.ledgerDao().updateEntryById(
            id = entryId,
            fingerprint = entryFingerprint(entryLike),
            type = type.name,
            amount = amount,
            category = resolvedCategory,
            description = encryptText(description.trim()).orEmpty(),
            merchant = encryptText(merchant?.trim()?.takeIf { it.isNotEmpty() }),
            spendingKind = resolvedKind.name,
            countedInExpense = type == EntryType.EXPENSE
        )
    }

    suspend fun deleteEntry(entryId: String) {
        if (entryId.isBlank()) return
        database.ledgerDao().deleteById(entryId)
    }

    suspend fun ingestParsedRecords(records: List<ParsedRecord>) {
        if (records.isEmpty()) return

        val existing = database.ledgerDao().getRecent(3_000).map { it.toDomain() }
        val ownedAccounts = database.ownedAccountDao().getAll().map { it.toDomain() }
        val ownerAliases = database.ownerAliasDao().getAll().map { it.alias }.toSet()
        val rules = database.classificationRuleDao().getAll().map { it.toDomain() }

        val classified = classifier.classifyRecords(
            records = records,
            existing = existing,
            ownedAccounts = ownedAccounts,
            ownerAliases = ownerAliases,
            classificationRules = rules
        )

        val seenFingerprints = existing.map(::entryFingerprint).toMutableSet()
        val deduplicated = classified.filter { entry ->
            seenFingerprints.add(entryFingerprint(entry))
        }
        if (deduplicated.isEmpty()) return

        database.ledgerDao().insertIgnore(deduplicated.map { it.toEntity() })
    }

    suspend fun ingestNotification(record: ParsedRecord) {
        ingestParsedRecords(listOf(record.copy(source = EntrySource.NOTIFICATION)))
    }

    private fun defaultCategoryFor(type: EntryType, spendingKind: SpendingKind): String {
        return when {
            type == EntryType.INCOME -> "수입"
            type == EntryType.TRANSFER -> "이체"
            spendingKind == SpendingKind.SUBSCRIPTION -> "구독"
            spendingKind == SpendingKind.INSTALLMENT -> "할부"
            spendingKind == SpendingKind.LOAN -> "대출"
            else -> "일반지출"
        }
    }

    private fun categoryBudgetKey(category: String): String = "CATEGORY:${category.lowercase()}"

    private fun entryFingerprint(entry: LedgerEntry): String {
        val epochMinute = entry.occurredAt.atZone(zoneId).toEpochSecond() / 60
        val normalizedDesc = entry.description.trim().lowercase()
        return "$epochMinute|${entry.type}|${entry.amount}|$normalizedDesc"
    }

    private fun LedgerEntryEntity.toDomain(): LedgerEntry {
        val type = runCatching { EntryType.valueOf(type) }.getOrDefault(EntryType.EXPENSE)
        val source = runCatching { EntrySource.valueOf(source) }.getOrDefault(EntrySource.MANUAL)
        val spendingKind = runCatching { SpendingKind.valueOf(spendingKind) }.getOrDefault(SpendingKind.NORMAL)

        return LedgerEntry(
            id = id,
            occurredAt = millisToDateTime(occurredAtMillis),
            amount = amount,
            type = type,
            category = category,
            description = decryptText(description).orEmpty(),
            merchant = decryptText(merchant),
            source = source,
            spendingKind = spendingKind,
            countedInExpense = countedInExpense,
            accountMask = accountMask,
            counterpartyName = decryptText(counterpartyName)
        )
    }

    private fun LedgerEntry.toEntity(fingerprint: String = entryFingerprint(this)): LedgerEntryEntity {
        return LedgerEntryEntity(
            id = id,
            fingerprint = fingerprint,
            occurredAtMillis = occurredAt.toEpochMillis(),
            amount = amount,
            type = type.name,
            category = category,
            description = encryptText(description).orEmpty(),
            merchant = encryptText(merchant),
            source = source.name,
            spendingKind = spendingKind.name,
            countedInExpense = countedInExpense,
            accountMask = accountMask,
            counterpartyName = encryptText(counterpartyName)
        )
    }

    private fun OwnedAccountEntity.toDomain(): OwnedAccount {
        return OwnedAccount(
            bank = bank,
            accountMask = accountMask,
            ownerName = decryptText(ownerName).orEmpty()
        )
    }

    private fun OwnedAccount.toEntity(): OwnedAccountEntity {
        return OwnedAccountEntity(
            accountMask = accountMask,
            bank = bank,
            ownerName = encryptText(ownerName).orEmpty()
        )
    }

    private fun InstallmentPlanEntity.toDomain(): InstallmentPlan {
        val parsedMonth = runCatching { YearMonth.parse(startMonth) }.getOrDefault(YearMonth.now())
        return InstallmentPlan(
            id = id,
            cardLast4 = cardLast4,
            merchant = decryptText(merchant).orEmpty(),
            monthlyAmount = monthlyAmount,
            totalMonths = totalMonths,
            startMonth = parsedMonth
        )
    }

    private fun InstallmentPlan.toEntity(): InstallmentPlanEntity {
        return InstallmentPlanEntity(
            id = id,
            cardLast4 = cardLast4,
            merchant = encryptText(merchant).orEmpty(),
            monthlyAmount = monthlyAmount,
            totalMonths = totalMonths,
            startMonth = startMonth.toString()
        )
    }

    private fun QuickTemplateEntity.toDomain(): QuickTemplate {
        return QuickTemplate(
            id = id,
            name = decryptText(name).orEmpty(),
            type = runCatching { EntryType.valueOf(type) }.getOrDefault(EntryType.EXPENSE),
            amount = amount,
            description = decryptText(description).orEmpty(),
            merchant = decryptText(merchant),
            category = category,
            spendingKind = runCatching { SpendingKind.valueOf(spendingKind) }.getOrDefault(SpendingKind.NORMAL),
            repeatMonthlyDay = repeatMonthlyDay,
            enabled = enabled
        )
    }

    private fun QuickTemplate.toEntity(): QuickTemplateEntity {
        return QuickTemplateEntity(
            id = id,
            name = encryptText(name).orEmpty(),
            type = type.name,
            amount = amount,
            description = encryptText(description).orEmpty(),
            merchant = encryptText(merchant),
            category = category,
            spendingKind = spendingKind.name,
            repeatMonthlyDay = repeatMonthlyDay,
            enabled = enabled,
            createdAtMillis = System.currentTimeMillis()
        )
    }

    private fun BudgetTargetEntity.toDomain(): BudgetTarget {
        return BudgetTarget(
            key = key,
            category = category,
            amount = amount
        )
    }

    private fun MonthlyClosingEntity.toDomain(): MonthlyClosing {
        return MonthlyClosing(
            month = runCatching { YearMonth.parse(month) }.getOrDefault(YearMonth.now()),
            carryIn = carryIn,
            expectedClosing = expectedClosing,
            actualClosing = actualClosing,
            delta = delta,
            closedAtMillis = closedAtMillis
        )
    }

    private fun ClassificationRuleEntity.toDomain(): ClassificationRule {
        return ClassificationRule(
            id = id,
            keyword = keyword,
            spendingKind = runCatching { SpendingKind.valueOf(spendingKind) }.getOrDefault(SpendingKind.NORMAL),
            category = category,
            enabled = enabled,
            createdAtMillis = createdAtMillis
        )
    }

    private fun millisToDateTime(value: Long): LocalDateTime {
        return Instant.ofEpochMilli(value).atZone(zoneId).toLocalDateTime()
    }

    private fun LocalDateTime.toEpochMillis(): Long {
        return atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun encryptText(value: String?): String? {
        return secureTextCipher?.encrypt(value) ?: value
    }

    private fun decryptText(value: String?): String? {
        return secureTextCipher?.decrypt(value) ?: value
    }

    companion object {
        const val TOTAL_BUDGET_KEY = "TOTAL"
    }
}
