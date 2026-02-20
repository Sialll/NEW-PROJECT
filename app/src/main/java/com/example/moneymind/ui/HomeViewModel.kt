package com.example.moneymind.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymind.BuildConfig
import com.example.moneymind.core.ServiceLocator
import com.example.moneymind.data.repo.LedgerRepository
import com.example.moneymind.domain.BudgetTarget
import com.example.moneymind.domain.ClassificationRule
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.InstallmentPlan
import com.example.moneymind.domain.InstallmentTracker
import com.example.moneymind.domain.LedgerEntry
import com.example.moneymind.domain.MonthlyClosing
import com.example.moneymind.domain.MonthlySummary
import com.example.moneymind.domain.OwnedAccount
import com.example.moneymind.domain.QuickTemplate
import com.example.moneymind.domain.SpendingKind
import com.example.moneymind.domain.SummaryCalculator
import com.example.moneymind.export.CsvExportType
import com.example.moneymind.export.CsvLedgerExporter
import com.example.moneymind.importer.StatementImporter
import com.example.moneymind.notification.BankNotificationListener
import com.example.moneymind.notification.NotificationAccessHelper
import com.example.moneymind.notification.NotificationMessageParser
import com.example.moneymind.notification.NotificationSourcePolicy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CategoryBudgetProgress(
    val category: String,
    val budget: Long,
    val used: Long,
    val remaining: Long
)

data class BudgetProgress(
    val totalBudget: Long? = null,
    val totalExpense: Long = 0L,
    val totalRemaining: Long? = null,
    val categoryProgress: List<CategoryBudgetProgress> = emptyList(),
    val overBudgetMessages: List<String> = emptyList()
)

data class ClosingPreview(
    val month: YearMonth = YearMonth.now(),
    val carryIn: Long = 0L,
    val expectedClosing: Long = 0L,
    val actualClosing: Long? = null,
    val delta: Long? = null,
    val isClosed: Boolean = false
)

data class CategoryTrend(
    val category: String,
    val currentMonthExpense: Long,
    val previousMonthExpense: Long,
    val change: Long
)

data class AdvancedReport(
    val month: YearMonth = YearMonth.now(),
    val currentExpense: Long = 0L,
    val previousExpense: Long = 0L,
    val quarterAvgExpense: Long = 0L,
    val currentIncome: Long = 0L,
    val previousIncome: Long = 0L,
    val quarterAvgIncome: Long = 0L,
    val latestAsset: Long? = null,
    val recurringLiability: Long = 0L,
    val topCategoryTrends: List<CategoryTrend> = emptyList()
)

data class HomeUiState(
    val ownerAliases: Set<String> = emptySet(),
    val accounts: List<OwnedAccount> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val quickTemplates: List<QuickTemplate> = emptyList(),
    val budgetTargets: List<BudgetTarget> = emptyList(),
    val monthlyClosings: List<MonthlyClosing> = emptyList(),
    val classificationRules: List<ClassificationRule> = emptyList(),
    val reviewCandidates: List<LedgerEntry> = emptyList(),
    val summary: MonthlySummary = MonthlySummary(
        month = YearMonth.now(),
        income = 0L,
        expense = 0L,
        transfer = 0L,
        subscriptionExpense = 0L,
        installmentExpense = 0L,
        loanExpense = 0L
    ),
    val budgetProgress: BudgetProgress = BudgetProgress(),
    val closingPreview: ClosingPreview = ClosingPreview(),
    val report: AdvancedReport = AdvancedReport(),
    val warnings: List<String> = emptyList(),
    val encryptionEnabled: Boolean = true,
    val notificationCaptureSupported: Boolean = BuildConfig.NOTIFICATION_CAPTURE_ENABLED,
    val notificationAccessEnabled: Boolean = false,
    val lastError: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LedgerRepository = ServiceLocator.repository(application)
    private val importer = StatementImporter()
    private val csvExporter = CsvLedgerExporter()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshNotificationAccess()
        bootstrapRecurringEntries()
        observePersistedState()
    }

    fun setOwnerAlias(alias: String) {
        viewModelScope.launch {
            runCatching {
                repository.addOwnerAlias(alias)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "Failed to save alias") }
            }
        }
    }

    fun registerOwnedAccount(bank: String, accountMask: String, ownerName: String) {
        viewModelScope.launch {
            runCatching {
                repository.registerOwnedAccount(
                    OwnedAccount(
                        bank = bank.ifBlank { "Bank" },
                        accountMask = accountMask,
                        ownerName = ownerName
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "Failed to save account") }
            }
        }
    }

    fun registerInstallment(
        cardLast4: String,
        merchant: String,
        monthlyAmount: Long,
        totalMonths: Int,
        startDate: LocalDate = LocalDate.now()
    ) {
        viewModelScope.launch {
            runCatching {
                if (cardLast4.isBlank() || merchant.isBlank() || monthlyAmount <= 0L || totalMonths <= 1) {
                    return@runCatching
                }
                repository.registerInstallment(
                    InstallmentPlan(
                        cardLast4 = cardLast4,
                        merchant = merchant,
                        monthlyAmount = monthlyAmount,
                        totalMonths = totalMonths,
                        startMonth = YearMonth.from(startDate)
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "Failed to save installment") }
            }
        }
    }

    fun addManualEntry(
        type: EntryType,
        amountText: String,
        description: String,
        merchant: String,
        category: String,
        spendingKind: SpendingKind
    ) {
        viewModelScope.launch {
            runCatching {
                val amount = amountText.filter(Char::isDigit).toLongOrNull()
                    ?: error("금액을 숫자로 입력해 주세요.")
                val normalizedDescription = description.trim()
                if (normalizedDescription.isBlank()) {
                    error("거래 내용을 입력해 주세요.")
                }

                val normalizedType = type
                val resolvedKind = if (normalizedType == EntryType.EXPENSE) spendingKind else SpendingKind.NORMAL
                val resolvedCategory = category.trim().ifBlank {
                    when {
                        normalizedType == EntryType.INCOME -> "수입"
                        resolvedKind == SpendingKind.SUBSCRIPTION -> "구독"
                        resolvedKind == SpendingKind.INSTALLMENT -> "할부"
                        resolvedKind == SpendingKind.LOAN -> "대출"
                        normalizedType == EntryType.TRANSFER -> "이체"
                        else -> "일반지출"
                    }
                }

                repository.addManualEntry(
                    LedgerEntry(
                        occurredAt = LocalDateTime.now(),
                        amount = amount,
                        type = normalizedType,
                        category = resolvedCategory,
                        description = normalizedDescription,
                        merchant = merchant.trim().ifBlank { null },
                        source = EntrySource.MANUAL,
                        spendingKind = resolvedKind
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "수동 입력 저장 실패") }
            }
        }
    }

    fun saveQuickTemplate(
        name: String,
        type: EntryType,
        amountText: String,
        description: String,
        merchant: String,
        category: String,
        spendingKind: SpendingKind,
        repeatMonthlyDayText: String
    ) {
        viewModelScope.launch {
            runCatching {
                val amount = amountText.filter(Char::isDigit).toLongOrNull()
                    ?: error("금액을 숫자로 입력해 주세요.")
                val templateName = name.trim().ifBlank { description.trim().take(18) }
                val repeatDay = repeatMonthlyDayText.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 31)

                repository.addQuickTemplate(
                    QuickTemplate(
                        name = templateName,
                        type = type,
                        amount = amount,
                        description = description.trim(),
                        merchant = merchant.trim().ifBlank { null },
                        category = category.trim(),
                        spendingKind = if (type == EntryType.EXPENSE) spendingKind else SpendingKind.NORMAL,
                        repeatMonthlyDay = repeatDay,
                        enabled = true
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
                repository.materializeRecurringTemplates(YearMonth.now())
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "템플릿 저장 실패") }
            }
        }
    }

    fun runQuickTemplateNow(templateId: String) {
        viewModelScope.launch {
            runCatching {
                repository.runQuickTemplateNow(templateId)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "템플릿 실행 실패") }
            }
        }
    }

    fun deleteQuickTemplate(templateId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteQuickTemplate(templateId)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "템플릿 삭제 실패") }
            }
        }
    }

    fun refreshRecurringEntries() {
        viewModelScope.launch {
            runCatching {
                repository.materializeRecurringTemplates(YearMonth.now())
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "반복 항목 반영 실패") }
            }
        }
    }

    fun setTotalBudget(amountText: String) {
        viewModelScope.launch {
            runCatching {
                val amount = amountText.filter(Char::isDigit).toLongOrNull() ?: 0L
                repository.setTotalBudget(amount)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "총 예산 저장 실패") }
            }
        }
    }

    fun setCategoryBudget(category: String, amountText: String) {
        viewModelScope.launch {
            runCatching {
                val amount = amountText.filter(Char::isDigit).toLongOrNull() ?: 0L
                repository.setCategoryBudget(category, amount)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "카테고리 예산 저장 실패") }
            }
        }
    }

    fun removeCategoryBudget(category: String) {
        viewModelScope.launch {
            runCatching {
                repository.removeCategoryBudget(category)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "카테고리 예산 삭제 실패") }
            }
        }
    }

    fun closeCurrentMonth(actualClosingText: String) {
        viewModelScope.launch {
            runCatching {
                val actual = actualClosingText.filter(Char::isDigit).toLongOrNull()
                repository.closeMonth(YearMonth.now(), actual)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "월 마감 저장 실패") }
            }
        }
    }

    fun saveClassificationRule(keyword: String, spendingKind: SpendingKind, category: String) {
        viewModelScope.launch {
            runCatching {
                repository.upsertClassificationRule(keyword, spendingKind, category)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "분류 룰 저장 실패") }
            }
        }
    }

    fun removeClassificationRule(ruleId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteClassificationRule(ruleId)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "분류 룰 삭제 실패") }
            }
        }
    }

    fun createRuleFromEntry(entryId: String, spendingKind: SpendingKind) {
        val entry = _uiState.value.entries.firstOrNull { it.id == entryId } ?: return
        val keyword = entry.merchant?.trim()?.takeIf { it.isNotBlank() }
            ?: entry.description.split(" ").firstOrNull { it.length >= 2 }
            ?: entry.description.take(8)

        saveClassificationRule(keyword = keyword, spendingKind = spendingKind, category = "")
    }

    fun updateEntry(
        entryId: String,
        type: EntryType,
        amountText: String,
        description: String,
        merchant: String,
        category: String,
        spendingKind: SpendingKind
    ) {
        viewModelScope.launch {
            runCatching {
                val amount = amountText.filter(Char::isDigit).toLongOrNull()
                    ?: error("금액을 숫자로 입력해 주세요.")
                val normalizedDescription = description.trim()
                if (normalizedDescription.isBlank()) {
                    error("거래 내용을 입력해 주세요.")
                }

                repository.updateEntry(
                    entryId = entryId,
                    type = type,
                    amount = amount,
                    description = normalizedDescription,
                    merchant = merchant,
                    category = category,
                    spendingKind = spendingKind
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "거래 수정 실패") }
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            runCatching {
                repository.deleteEntry(entryId)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "거래 삭제 실패") }
            }
        }
    }

    fun injectTestNotification(preset: NotificationTestPreset) {
        viewModelScope.launch {
            runCatching {
                val payload = preset.toPayload()
                if (!NotificationSourcePolicy.isSupported(payload.packageName, payload.title, payload.text)) {
                    error("테스트 알림이 지원 소스로 인식되지 않았습니다.")
                }
                val parsed = NotificationMessageParser.parse(payload.title, payload.text)
                    ?: error("테스트 알림 파싱 실패")
                repository.ingestNotification(
                    parsed.copy(
                        raw = parsed.raw + mapOf(
                            "test_mode" to "true",
                            "test_package" to payload.packageName,
                            "test_preset" to preset.name
                        )
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "테스트 알림 주입 실패") }
            }
        }
    }

    fun importStatement(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val parsed = importer.import(context, uri)
                if (parsed.isEmpty()) {
                    error("No transactions detected. Check the file format or column headers.")
                }
                repository.ingestParsedRecords(parsed)
                repository.materializeRecurringTemplates(YearMonth.now())
                repository.closeMonth(YearMonth.now(), actualClosing = null)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "Import failed") }
            }
        }
    }

    fun exportCsv(context: Context, uri: Uri, exportType: CsvExportType) {
        viewModelScope.launch {
            runCatching {
                val entries = _uiState.value.entries
                csvExporter.export(context, uri, entries, exportType)
            }.onSuccess {
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "CSV export failed") }
            }
        }
    }

    fun refreshNotificationAccess() {
        val context = getApplication<Application>().applicationContext
        val accessEnabled = BuildConfig.NOTIFICATION_CAPTURE_ENABLED &&
            NotificationAccessHelper.isAccessGranted(context, BankNotificationListener::class.java)

        if (accessEnabled) {
            // Some OEMs do not reconnect listener immediately after grant.
            BankNotificationListener.requestRebindSafely(context)
        }

        _uiState.update {
            it.copy(
                notificationCaptureSupported = BuildConfig.NOTIFICATION_CAPTURE_ENABLED,
                notificationAccessEnabled = accessEnabled
            )
        }
    }

    private fun bootstrapRecurringEntries() {
        viewModelScope.launch {
            runCatching {
                repository.materializeRecurringTemplates(YearMonth.now())
                repository.closeMonth(YearMonth.now(), actualClosing = null)
            }
        }
    }

    private fun observePersistedState() {
        viewModelScope.launch {
            combine(
                combine(
                    repository.entriesFlow,
                    repository.ownedAccountsFlow,
                    repository.ownerAliasesFlow,
                    repository.installmentPlansFlow
                ) { entries, accounts, aliases, plans ->
                    CorePersistedState(
                        entries = entries,
                        accounts = accounts,
                        aliases = aliases,
                        plans = plans
                    )
                },
                combine(
                    repository.quickTemplatesFlow,
                    repository.budgetTargetsFlow,
                    repository.monthlyClosingsFlow,
                    repository.classificationRulesFlow
                ) { templates, budgets, closings, rules ->
                    ExtendedPersistedState(
                        templates = templates,
                        budgets = budgets,
                        closings = closings,
                        rules = rules
                    )
                }
            ) { core, extended ->
                PersistedState(
                    entries = core.entries,
                    accounts = core.accounts,
                    aliases = core.aliases,
                    plans = core.plans,
                    templates = extended.templates,
                    budgets = extended.budgets,
                    closings = extended.closings,
                    rules = extended.rules
                )
            }.collect { persisted ->
                val month = YearMonth.now()
                val summary = SummaryCalculator.summarize(persisted.entries, month)
                val warnings = buildWarnings(persisted.plans, month)
                val budgetProgress = buildBudgetProgress(persisted.entries, persisted.budgets, month)
                val closingPreview = buildClosingPreview(persisted.entries, persisted.closings, month)
                val report = buildAdvancedReport(persisted.entries, persisted.closings, month)
                val reviewCandidates = buildReviewCandidates(persisted.entries, persisted.rules, month)

                _uiState.update { state ->
                    state.copy(
                        ownerAliases = persisted.aliases,
                        accounts = persisted.accounts,
                        entries = persisted.entries,
                        quickTemplates = persisted.templates,
                        budgetTargets = persisted.budgets,
                        monthlyClosings = persisted.closings,
                        classificationRules = persisted.rules,
                        reviewCandidates = reviewCandidates,
                        summary = summary,
                        budgetProgress = budgetProgress,
                        closingPreview = closingPreview,
                        report = report,
                        warnings = warnings
                    )
                }
            }
        }
    }

    private fun buildWarnings(plans: List<InstallmentPlan>, month: YearMonth): List<String> {
        if (plans.isEmpty()) return emptyList()
        val tracker = InstallmentTracker()
        plans.forEach(tracker::register)
        return tracker.projectedWarnings(month).map {
            "${it.merchant} ${it.amount} (remaining ${it.remainingMonths} months)"
        }
    }

    private fun buildBudgetProgress(
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
        val totalBudget = targets.firstOrNull { it.key == LedgerRepository.TOTAL_BUDGET_KEY }?.amount
        val totalRemaining = totalBudget?.minus(totalExpense)

        val categoryUsage = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val categoryTargets = targets.filter { !it.category.isNullOrBlank() }
        val categoryProgress = categoryTargets.map { target ->
            val category = target.category.orEmpty()
            val used = categoryUsage[category] ?: 0L
            CategoryBudgetProgress(
                category = category,
                budget = target.amount,
                used = used,
                remaining = target.amount - used
            )
        }.sortedBy { it.remaining }

        val overMessages = mutableListOf<String>()
        if (totalRemaining != null && totalRemaining < 0L) {
            overMessages += "총예산 초과 ${abs(totalRemaining)}원"
        }
        categoryProgress
            .filter { it.remaining < 0L }
            .forEach { overMessages += "${it.category} 예산 초과 ${abs(it.remaining)}원" }

        return BudgetProgress(
            totalBudget = totalBudget,
            totalExpense = totalExpense,
            totalRemaining = totalRemaining,
            categoryProgress = categoryProgress,
            overBudgetMessages = overMessages
        )
    }

    private fun buildClosingPreview(
        entries: List<LedgerEntry>,
        closings: List<MonthlyClosing>,
        month: YearMonth
    ): ClosingPreview {
        val current = closings.firstOrNull { it.month == month }
        val previous = closings
            .filter { monthToOrder(it.month) < monthToOrder(month) }
            .maxByOrNull { monthToOrder(it.month) }

        val carryIn = current?.carryIn
            ?: previous?.actualClosing
            ?: previous?.expectedClosing
            ?: 0L

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
        val actual = current?.actualClosing
        val delta = actual?.minus(expected)

        return ClosingPreview(
            month = month,
            carryIn = carryIn,
            expectedClosing = expected,
            actualClosing = actual,
            delta = delta,
            isClosed = actual != null
        )
    }

    private fun buildAdvancedReport(
        entries: List<LedgerEntry>,
        closings: List<MonthlyClosing>,
        month: YearMonth
    ): AdvancedReport {
        val previousMonth = month.minusMonths(1)
        val summaryCurrent = SummaryCalculator.summarize(entries, month)
        val summaryPrevious = SummaryCalculator.summarize(entries, previousMonth)

        val quarterMonths = listOf(month, month.minusMonths(1), month.minusMonths(2))
        val quarterSummaries = quarterMonths.map { SummaryCalculator.summarize(entries, it) }

        val currentCategoryMap = entries
            .filter {
                it.type == EntryType.EXPENSE &&
                    it.countedInExpense &&
                    YearMonth.from(it.occurredAt) == month
            }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val previousCategoryMap = entries
            .filter {
                it.type == EntryType.EXPENSE &&
                    it.countedInExpense &&
                    YearMonth.from(it.occurredAt) == previousMonth
            }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val trendCategories = (currentCategoryMap.keys + previousCategoryMap.keys)
            .toSet()
            .sortedByDescending { category -> currentCategoryMap[category] ?: 0L }
            .take(5)

        val trends = trendCategories.map { category ->
            val currentExpense = currentCategoryMap[category] ?: 0L
            val previousExpense = previousCategoryMap[category] ?: 0L
            CategoryTrend(
                category = category,
                currentMonthExpense = currentExpense,
                previousMonthExpense = previousExpense,
                change = currentExpense - previousExpense
            )
        }

        val latestAsset = closings
            .maxByOrNull { monthToOrder(it.month) }
            ?.let { it.actualClosing ?: it.expectedClosing }

        val recurringLiability =
            summaryCurrent.subscriptionExpense + summaryCurrent.installmentExpense + summaryCurrent.loanExpense

        return AdvancedReport(
            month = month,
            currentExpense = summaryCurrent.expense,
            previousExpense = summaryPrevious.expense,
            quarterAvgExpense = quarterSummaries.map { it.expense }.average().toLong(),
            currentIncome = summaryCurrent.income,
            previousIncome = summaryPrevious.income,
            quarterAvgIncome = quarterSummaries.map { it.income }.average().toLong(),
            latestAsset = latestAsset,
            recurringLiability = recurringLiability,
            topCategoryTrends = trends
        )
    }

    private fun buildReviewCandidates(
        entries: List<LedgerEntry>,
        rules: List<ClassificationRule>,
        month: YearMonth
    ): List<LedgerEntry> {
        val ruleKeywords = rules.map { it.keyword.lowercase() }
        return entries
            .asSequence()
            .filter { it.type == EntryType.EXPENSE && it.countedInExpense }
            .filter { YearMonth.from(it.occurredAt) == month }
            .filter { it.source != EntrySource.MANUAL }
            .filter { it.spendingKind == SpendingKind.NORMAL || it.category.contains("기타") }
            .filter { entry ->
                val merged = "${entry.description.lowercase()} ${entry.merchant.orEmpty().lowercase()}"
                ruleKeywords.none { keyword -> keyword.isNotBlank() && merged.contains(keyword) }
            }
            .sortedByDescending { it.occurredAt }
            .take(12)
            .toList()
    }

    private fun monthToOrder(month: YearMonth): Int = month.year * 12 + month.monthValue

    private fun NotificationTestPreset.toPayload(): TestNotificationPayload {
        return when (this) {
            NotificationTestPreset.EXPENSE_CARD -> TestNotificationPayload(
                packageName = "com.kakaobank.channel",
                title = "카카오뱅크 카드",
                text = "체크카드 결제 12,300원 스타벅스"
            )
            NotificationTestPreset.INCOME_SALARY -> TestNotificationPayload(
                packageName = "com.kakaobank.channel",
                title = "카카오뱅크",
                text = "입금 2,500,000원 급여"
            )
            NotificationTestPreset.EXPENSE_LOAN -> TestNotificationPayload(
                packageName = "com.kakaobank.channel",
                title = "카카오뱅크",
                text = "대출 이자 출금 180,000원"
            )
        }
    }

    enum class NotificationTestPreset {
        EXPENSE_CARD,
        INCOME_SALARY,
        EXPENSE_LOAN
    }

    private data class TestNotificationPayload(
        val packageName: String,
        val title: String,
        val text: String
    )

    private data class CorePersistedState(
        val entries: List<LedgerEntry>,
        val accounts: List<OwnedAccount>,
        val aliases: Set<String>,
        val plans: List<InstallmentPlan>
    )

    private data class ExtendedPersistedState(
        val templates: List<QuickTemplate>,
        val budgets: List<BudgetTarget>,
        val closings: List<MonthlyClosing>,
        val rules: List<ClassificationRule>
    )

    private data class PersistedState(
        val entries: List<LedgerEntry>,
        val accounts: List<OwnedAccount>,
        val aliases: Set<String>,
        val plans: List<InstallmentPlan>,
        val templates: List<QuickTemplate>,
        val budgets: List<BudgetTarget>,
        val closings: List<MonthlyClosing>,
        val rules: List<ClassificationRule>
    )
}
