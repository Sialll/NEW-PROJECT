package com.example.moneymind.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
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
    val categoryOptions: List<String> = defaultCategoryOptions,
    val pinnedCategories: Set<String> = emptySet(),
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
    val smsPermissionGranted: Boolean = false,
    val lastError: String? = null
) {
    companion object {
        val defaultCategoryOptions = emptyList<String>()
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: LedgerRepository = ServiceLocator.repository(application)
    private val importer = StatementImporter()
    private val csvExporter = CsvLedgerExporter()
    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val customCategories = MutableStateFlow(loadCustomCategories())
    private val pinnedCategories = MutableStateFlow(loadPinnedCategories())
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshNotificationAccess()
        refreshSmsPermissionAccess()
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
                val resolvedCategory = category.trim()
                if (resolvedCategory.isBlank()) {
                    error("카테고리를 먼저 선택하거나 추가해 주세요.")
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

    fun addCustomCategory(category: String) {
        val normalized = category.trim()
        if (normalized.isBlank()) return

        val updated = customCategories.value + normalized
        customCategories.value = updated
        prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, updated).apply()

        _uiState.update { state ->
            val merged = (state.categoryOptions + normalized)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            state.copy(categoryOptions = merged)
        }
    }

    fun togglePinnedCategory(category: String) {
        val normalized = category.trim()
        if (normalized.isBlank()) return

        val updated = if (normalized in pinnedCategories.value) {
            pinnedCategories.value - normalized
        } else {
            pinnedCategories.value + normalized
        }
        pinnedCategories.value = updated
        prefs.edit().putStringSet(PREF_PINNED_CATEGORIES, updated).apply()
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
                val resolvedCategory = category.trim()
                if (resolvedCategory.isBlank()) {
                    error("템플릿 저장 전에 카테고리를 선택해 주세요.")
                }

                repository.addQuickTemplate(
                    QuickTemplate(
                        name = templateName,
                        type = type,
                        amount = amount,
                        description = description.trim(),
                        merchant = merchant.trim().ifBlank { null },
                        category = resolvedCategory,
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
                if (category.isNotBlank()) {
                    addCustomCategory(category)
                }
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "분류 룰 저장 실패") }
            }
        }
    }

    fun saveNaturalLanguageRule(command: String) {
        viewModelScope.launch {
            runCatching {
                val parsed = parseNaturalLanguageRule(command)
                    ?: error("조건 문장을 이해하지 못했습니다. 예: 토스 내 계좌 이체 건은 소비로 분류")

                repository.upsertClassificationRule(
                    keyword = parsed.keyword,
                    spendingKind = parsed.spendingKind,
                    category = parsed.category,
                    forcedType = parsed.forcedType
                )
                parsed
            }.onSuccess { parsed ->
                if (parsed.category.isNotBlank()) {
                    addCustomCategory(parsed.category)
                }
                _uiState.update { it.copy(lastError = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(lastError = error.message ?: "조건 저장 실패") }
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

    fun refreshSmsPermissionAccess() {
        val context = getApplication<Application>().applicationContext
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.update { it.copy(smsPermissionGranted = granted) }
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
                },
                customCategories,
                pinnedCategories
            ) { persisted, customCategorySet, pinnedCategorySet ->
                Triple(persisted, customCategorySet, pinnedCategorySet)
            }.collect { (persisted, customCategorySet, pinnedCategorySet) ->
                val month = YearMonth.now()
                val summary = SummaryCalculator.summarize(persisted.entries, month)
                val warnings = buildWarnings(persisted.plans, month)
                val budgetProgress = buildBudgetProgress(persisted.entries, persisted.budgets, month)
                val closingPreview = buildClosingPreview(persisted.entries, persisted.closings, month)
                val report = buildAdvancedReport(persisted.entries, persisted.closings, month)
                val reviewCandidates = buildReviewCandidates(persisted.entries, persisted.rules, month)
                val categoryOptions = buildCategoryOptions(
                    entries = persisted.entries,
                    templates = persisted.templates,
                    rules = persisted.rules,
                    customCategories = customCategorySet
                )

                _uiState.update { state ->
                    state.copy(
                        ownerAliases = persisted.aliases,
                        accounts = persisted.accounts,
                        entries = persisted.entries,
                        quickTemplates = persisted.templates,
                        budgetTargets = persisted.budgets,
                        monthlyClosings = persisted.closings,
                        classificationRules = persisted.rules,
                        categoryOptions = categoryOptions,
                        pinnedCategories = pinnedCategorySet,
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

    private fun parseNaturalLanguageRule(command: String): ParsedNaturalRule? {
        val normalized = command.trim()
        if (normalized.isBlank()) return null

        val forcedType = detectForcedType(normalized)
        val keywordBase = extractKeywordFromRuleText(normalized)
        if (keywordBase.length < 2) return null

        val spendingKind = when {
            containsAny(normalized, listOf("구독", "정기결제", "멤버십", "자동결제")) -> SpendingKind.SUBSCRIPTION
            containsAny(normalized, listOf("할부", "분할", "무이자", "개월")) -> SpendingKind.INSTALLMENT
            containsAny(normalized, listOf("대출", "이자", "원리금", "카드론", "현금서비스")) -> SpendingKind.LOAN
            else -> SpendingKind.NORMAL
        }

        val category = extractCategoryFromCommand(normalized).ifBlank {
            when (forcedType) {
                EntryType.INCOME -> "수입"
                EntryType.TRANSFER -> "이체"
                else -> when (spendingKind) {
                    SpendingKind.SUBSCRIPTION -> "구독"
                    SpendingKind.INSTALLMENT -> "할부"
                    SpendingKind.LOAN -> "대출"
                    SpendingKind.NORMAL -> "일반지출"
                }
            }
        }

        return ParsedNaturalRule(
            keyword = keywordBase.lowercase(),
            spendingKind = spendingKind,
            category = category,
            forcedType = forcedType
        )
    }

    private fun detectForcedType(command: String): EntryType? {
        return when {
            containsAny(command, listOf("소비", "지출", "결제", "사용")) -> EntryType.EXPENSE
            containsAny(command, listOf("수입", "입금", "정산", "벌")) -> EntryType.INCOME
            containsAny(command, listOf("이체", "송금", "옮김")) -> EntryType.TRANSFER
            else -> null
        }
    }

    private fun extractKeywordFromRuleText(command: String): String {
        val patterns = listOf(
            Regex("(.+?)\\s*(은|는|이면|면|인\\s*경우|일\\s*때|일때|포함되면|있으면)"),
            Regex("(.+?)\\s*(을|를)\\s*.*(분류|처리|설정)"),
            Regex("(.+?)\\s*(무조건|항상)")
        )
        val fromPattern = patterns.firstNotNullOfOrNull { regex ->
            regex.find(command)?.groupValues?.getOrNull(1)?.trim()
        }

        return (fromPattern ?: command)
            .replace("건", " ")
            .replace("거래", " ")
            .replace("문자", " ")
            .replace("알림", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsAny(text: String, tokens: List<String>): Boolean {
        return tokens.any { token -> text.contains(token, ignoreCase = true) }
    }

    private fun extractCategoryFromCommand(command: String): String {
        val regex = Regex("(카테고리|분류)\\s*(은|는|:)?\\s*([\\p{L}\\p{N}/_-]{2,20})")
        return regex.find(command)?.groupValues?.getOrNull(3)?.trim().orEmpty()
    }

    private fun buildCategoryOptions(
        entries: List<LedgerEntry>,
        templates: List<QuickTemplate>,
        rules: List<ClassificationRule>,
        customCategories: Set<String>
    ): List<String> {
        val dynamic = entries.map { it.category } +
            templates.map { it.category } +
            rules.map { it.category } +
            customCategories

        return (HomeUiState.defaultCategoryOptions + dynamic)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    private fun loadCustomCategories(): Set<String> {
        return prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    private fun loadPinnedCategories(): Set<String> {
        return prefs.getStringSet(PREF_PINNED_CATEGORIES, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
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

        val overMessages = mutableListOf<String>()
        if (totalRemaining != null && totalRemaining < 0L) {
            overMessages += "총예산 초과 ${abs(totalRemaining)}원"
        }

        return BudgetProgress(
            totalBudget = totalBudget,
            totalExpense = totalExpense,
            totalRemaining = totalRemaining,
            categoryProgress = emptyList(),
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

    private data class ParsedNaturalRule(
        val keyword: String,
        val spendingKind: SpendingKind,
        val category: String,
        val forcedType: EntryType?
    )

    companion object {
        private const val PREFS_NAME = "moneymind_preferences"
        private const val PREF_CUSTOM_CATEGORIES = "custom_categories"
        private const val PREF_PINNED_CATEGORIES = "pinned_categories"
    }
}
