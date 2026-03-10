package com.example.moneymind.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moneymind.domain.EntryType
import com.example.moneymind.domain.LedgerEntry
import com.example.moneymind.domain.MonthlySummary
import com.example.moneymind.domain.SpendingKind
import com.example.moneymind.export.CsvExportType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt



@Composable
internal fun OptionsPage(
    state: HomeUiState,
    optionSection: OptionSection,
    onOptionSectionChange: (OptionSection) -> Unit,
    cardLast4Input: String,
    installmentMerchantInput: String,
    monthlyAmountInput: String,
    installmentMonthsInput: String,
    onImport: () -> Unit,
    onExportBank: () -> Unit,
    onExportAnalysis: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshNotification: () -> Unit,
    onToggleNotificationSource: (String, Boolean) -> Unit,
    onToggleAllNotificationSources: (Boolean) -> Unit,
    onInjectExpenseNotification: () -> Unit,
    onInjectIncomeNotification: () -> Unit,
    onInjectLoanNotification: () -> Unit,
    onCardLast4Change: (String) -> Unit,
    onInstallmentMerchantChange: (String) -> Unit,
    onMonthlyAmountChange: (String) -> Unit,
    onInstallmentMonthsChange: (String) -> Unit,
    onSaveInstallment: () -> Unit,
    onSetTotalBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String) -> Unit,
    onRemoveCategoryBudget: (String) -> Unit,
    onCloseMonth: (String) -> Unit,
    onClearAllRecords: () -> Unit,
    onClearRecordsByPeriod: (String, String) -> Unit,
    onFactoryReset: () -> Unit,
    onSaveRule: (String, SpendingKind, String) -> Unit,
    onSaveNaturalRule: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onCreateRuleFromEntry: (String, SpendingKind) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF3FF))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("옵션", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("파일/등록/분석 기능은 옵션에서 관리합니다.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            modifier = Modifier.testTag("options_section_file"),
                            selected = optionSection == OptionSection.FILE,
                            onClick = { onOptionSectionChange(OptionSection.FILE) },
                            label = { Text("파일") }
                        )
                        FilterChip(
                            modifier = Modifier.testTag("options_section_profile"),
                            selected = optionSection == OptionSection.PROFILE,
                            onClick = { onOptionSectionChange(OptionSection.PROFILE) },
                            label = { Text("등록 관리") }
                        )
                        FilterChip(
                            modifier = Modifier.testTag("options_section_control"),
                            selected = optionSection == OptionSection.CONTROL,
                            onClick = { onOptionSectionChange(OptionSection.CONTROL) },
                            label = { Text("예산/분석") }
                        )
                    }
                }
            }
        }

        when (optionSection) {
            OptionSection.FILE -> {
                item {
                    NotificationStatusCard(
                        state = state,
                        onOpenSettings = onOpenSettings,
                        onRefreshStatus = onRefreshNotification
                    )
                }
                item {
                    NotificationSourceFilterCard(
                        state = state,
                        onToggleSource = onToggleNotificationSource,
                        onToggleAllSources = onToggleAllNotificationSources
                    )
                }
                item {
                    FileOptionSection(
                        context = context,
                        onImport = onImport,
                        onExportBank = onExportBank,
                        onExportAnalysis = onExportAnalysis,
                        onInjectExpenseNotification = onInjectExpenseNotification,
                        onInjectIncomeNotification = onInjectIncomeNotification,
                        onInjectLoanNotification = onInjectLoanNotification
                    )
                }
            }
            OptionSection.PROFILE -> {
                item {
                    ProfileOptionSection(
                        cardLast4Input = cardLast4Input,
                        installmentMerchantInput = installmentMerchantInput,
                        monthlyAmountInput = monthlyAmountInput,
                        installmentMonthsInput = installmentMonthsInput,
                        onCardLast4Change = onCardLast4Change,
                        onInstallmentMerchantChange = onInstallmentMerchantChange,
                        onMonthlyAmountChange = onMonthlyAmountChange,
                        onInstallmentMonthsChange = onInstallmentMonthsChange,
                        onSaveInstallment = onSaveInstallment
                    )
                }
            }

            OptionSection.CONTROL -> {
                item {
                    ControlOptionSection(
                        state = state,
                        onSetTotalBudget = onSetTotalBudget,
                        onSetCategoryBudget = onSetCategoryBudget,
                        onRemoveCategoryBudget = onRemoveCategoryBudget,
                        onCloseMonth = onCloseMonth,
                        onClearAllRecords = onClearAllRecords,
                        onClearRecordsByPeriod = onClearRecordsByPeriod,
                        onFactoryReset = onFactoryReset,
                        onSaveRule = onSaveRule,
                        onSaveNaturalRule = onSaveNaturalRule,
                        onRemoveRule = onRemoveRule,
                        onCreateRuleFromEntry = onCreateRuleFromEntry
                    )
                }
            }
        }

        if (!state.lastError.isNullOrBlank()) {
            item { Text("오류: ${state.lastError}", color = MaterialTheme.colorScheme.error) }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun FileOptionSection(
    context: Context,
    onImport: () -> Unit,
    onExportBank: () -> Unit,
    onExportAnalysis: () -> Unit,
    onInjectExpenseNotification: () -> Unit,
    onInjectIncomeNotification: () -> Unit,
    onInjectLoanNotification: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("파일 입출력", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("읽기: CSV/엑셀/PPF(PDF)")
                }
                Button(onClick = onExportBank, modifier = Modifier.fillMaxWidth()) {
                    Text("내보내기: 은행용 CSV")
                }
                Button(onClick = onExportAnalysis, modifier = Modifier.fillMaxWidth()) {
                    Text("내보내기: 분석 CSV")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF)),
            border = BorderStroke(1.dp, Color(0xFF6C7EA0))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("PC 즉시 테스트", fontWeight = FontWeight.Bold)
                Text("가짜 알림으로 자동 분류 흐름(지출/수입/대출)을 바로 점검합니다.")
                Button(onClick = onInjectExpenseNotification, modifier = Modifier.fillMaxWidth()) {
                    Text("테스트 알림: 카드 지출")
                }
                Button(onClick = onInjectIncomeNotification, modifier = Modifier.fillMaxWidth()) {
                    Text("테스트 알림: 급여 수입")
                }
                Button(onClick = onInjectLoanNotification, modifier = Modifier.fillMaxWidth()) {
                    Text("테스트 알림: 대출 이자")
                }
            }
        }

        PrivacyNoticeCard(
            onOpenPolicy = { openPrivacyPolicy(context) }
        )
    }
}

@Composable
internal fun ManualEntrySection(
    state: HomeUiState,
    manualCategorySeedOptions: List<String>,
    onManualCategoryChange: (String) -> Unit,
    onAddCategoryOption: (String) -> Unit,
    onRemoveCategoryOption: (String) -> Unit,
    manualType: EntryType,
    manualKind: SpendingKind,
    amount: String,
    description: String,
    merchant: String,
    category: String,
    onTypeChange: (EntryType) -> Unit,
    onKindChange: (SpendingKind) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onSave: () -> Unit,
    onLoadManualFromEntry: (LedgerEntry) -> Unit,
    onSaveTemplate: (String, String) -> Unit,
    onRunTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRefreshRecurring: () -> Unit
) {
    var templateName by rememberSaveable { mutableStateOf("") }
    var repeatDayText by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("수동 입력", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER).forEach { type ->
                        FilterChip(
                            selected = manualType == type,
                            onClick = { onTypeChange(type) },
                            label = { Text(entryTypeLabel(type)) }
                        )
                    }
                }

                if (manualType == EntryType.EXPENSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SpendingKind.NORMAL to "일반",
                            SpendingKind.SUBSCRIPTION to "구독",
                            SpendingKind.INSTALLMENT to "할부",
                            SpendingKind.LOAN to "대출"
                        ).forEach { (kind, label) ->
                            FilterChip(
                                selected = manualKind == kind,
                                onClick = { onKindChange(kind) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                val normalizedManualCategory = category.trim()
                val allManualCategoryOptions = remember(
                    state.categoryOptions,
                    manualCategorySeedOptions,
                    normalizedManualCategory
                ) {
                    (state.categoryOptions + manualCategorySeedOptions + normalizedManualCategory)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                }

                SupportingText(
                    if (category.isBlank()) {
                        "카테고리를 선택하면 수동 입력 저장 시 바로 반영됩니다."
                    } else {
                        "선택된 카테고리: $category"
                    }
                )
                CategorySelectField(
                    value = category,
                    options = allManualCategoryOptions,
                    onValueChange = onManualCategoryChange,
                    onAddCategory = { newCategory ->
                        onAddCategoryOption(newCategory)
                        onManualCategoryChange(newCategory)
                    },
                    onDeleteCategory = { removed ->
                        if (category == removed) {
                            onManualCategoryChange("")
                        }
                        onRemoveCategoryOption(removed)
                    },
                    deletableCategoryOptions = state.customCategories,
                    label = "카테고리 선택"
                )

                CleanField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = "금액",
                    keyboardType = KeyboardType.Number
                )
                val currentAmount = amount.toLongOrNull() ?: 0L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10_000L, 30_000L, 50_000L, 100_000L).forEach { delta ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val updated = (currentAmount + delta).coerceAtLeast(0L)
                                onAmountChange(updated.toString())
                            },
                            label = { Text("+${formatWon(delta)}") }
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            val updated = (currentAmount - 10_000L).coerceAtLeast(0L)
                            onAmountChange(updated.toString())
                        },
                        label = { Text("-${formatWon(10_000L)}") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onAmountChange("") },
                        label = { Text("초기화") }
                    )
                }
                CleanField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = "거래 내용"
                )
                CleanField(
                    value = merchant,
                    onValueChange = onMerchantChange,
                    label = "가맹점/거래처 (선택)"
                )

                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text("수동 입력 저장")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F8FF))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("빠른 입력 템플릿", fontWeight = FontWeight.SemiBold)
                        Text(
                            "자주 쓰는 거래를 저장해 두고, 버튼 한 번으로 다시 입력하는 기능입니다. " +
                                "반복일을 넣으면 매월 자동으로 생성됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                        CleanField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = "템플릿 이름 (비우면 거래 내용 사용)"
                        )
                        CleanField(
                            value = repeatDayText,
                            onValueChange = { repeatDayText = it.filter(Char::isDigit).take(2) },
                            label = "매월 반복일 (선택, 1~31)",
                            keyboardType = KeyboardType.Number
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSaveTemplate(templateName, repeatDayText) }) {
                                Text("템플릿 저장")
                            }
                            Button(onClick = onRefreshRecurring) {
                                Text("반복 반영")
                            }
                        }
                    }
                }
            }
        }

        if (state.quickTemplates.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("저장된 템플릿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.quickTemplates.take(12).forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val repeatLabel = template.repeatMonthlyDay?.let { "매월 ${it}일" } ?: "반복 없음"
                                Text("${template.name} | ${template.amount}원", fontWeight = FontWeight.SemiBold)
                                Text("${template.description} (${repeatLabel})", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onRunTemplate(template.id) }) { Text("즉시 추가") }
                                    TextButton(onClick = { onDeleteTemplate(template.id) }) { Text("삭제", color = Color(0xFFB42318)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("최근 거래 재사용", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.entries
                        .filter { YearMonth.from(it.occurredAt) == state.summary.month }
                        .take(8)
                        .forEach { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("${entry.description} | ${entry.amount}원", fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { onLoadManualFromEntry(entry) }) { Text("불러오기") }
                                        TextButton(onClick = {
                                            onLoadManualFromEntry(entry)
                                            onSave()
                                        }) { Text("바로추가") }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun ProfileOptionSection(
    cardLast4Input: String,
    installmentMerchantInput: String,
    monthlyAmountInput: String,
    installmentMonthsInput: String,
    onCardLast4Change: (String) -> Unit,
    onInstallmentMerchantChange: (String) -> Unit,
    onMonthlyAmountChange: (String) -> Unit,
    onInstallmentMonthsChange: (String) -> Unit,
    onSaveInstallment: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("등록 관리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("할부/대출 건은 월 납부 스케줄만 등록해 두세요.")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("할부/대출 등록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("자동 인식 누락 대비용. (월 납부액/개월 수/카드/사용처)")
                CleanField(value = cardLast4Input, onValueChange = onCardLast4Change, label = "어디 카드")
                CleanField(value = installmentMerchantInput, onValueChange = onInstallmentMerchantChange, label = "사용처")
                CleanField(
                    value = monthlyAmountInput,
                    onValueChange = onMonthlyAmountChange,
                    label = "월 납부액",
                    keyboardType = KeyboardType.Number
                )
                CleanField(
                    value = installmentMonthsInput,
                    onValueChange = onInstallmentMonthsChange,
                    label = "총 개월 수",
                    keyboardType = KeyboardType.Number
                )
                Button(onClick = onSaveInstallment) { Text("등록 저장") }
            }
        }
    }
}

@Composable
private fun ControlOptionSection(
    state: HomeUiState,
    onSetTotalBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String) -> Unit,
    onRemoveCategoryBudget: (String) -> Unit,
    onCloseMonth: (String) -> Unit,
    onClearAllRecords: () -> Unit,
    onClearRecordsByPeriod: (String, String) -> Unit,
    onFactoryReset: () -> Unit,
    onSaveRule: (String, SpendingKind, String) -> Unit,
    onSaveNaturalRule: (String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onCreateRuleFromEntry: (String, SpendingKind) -> Unit
) {
    var totalBudgetInput by rememberSaveable { mutableStateOf("") }
    var categoryBudgetInput by rememberSaveable { mutableStateOf("") }
    var selectedBudgetCategory by rememberSaveable { mutableStateOf("") }
    var closingActualInput by rememberSaveable { mutableStateOf("") }
    var ruleKeywordInput by rememberSaveable { mutableStateOf("") }
    var ruleCategoryInput by rememberSaveable { mutableStateOf("") }
    var naturalRuleInput by rememberSaveable { mutableStateOf("") }
    var resetStartDateInput by rememberSaveable { mutableStateOf("") }
    var resetEndDateInput by rememberSaveable { mutableStateOf("") }
    var showClearAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showClearPeriodConfirm by rememberSaveable { mutableStateOf(false) }
    var showFactoryResetConfirm by rememberSaveable { mutableStateOf(false) }
    var ruleKindName by rememberSaveable { mutableStateOf(SpendingKind.SUBSCRIPTION.name) }
    val ruleKind = runCatching { SpendingKind.valueOf(ruleKindName) }.getOrDefault(SpendingKind.SUBSCRIPTION)
    val categoryBudgetOptions = remember(
        state.categoryOptions,
        state.budgetProgress.categoryProgress,
        selectedBudgetCategory
    ) {
        (state.categoryOptions + state.budgetProgress.categoryProgress.map { it.category } + selectedBudgetCategory)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F8FF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("예산/월간 목표", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("총 지출: ${state.budgetProgress.totalExpense}원")
                Text("총 예산: ${state.budgetProgress.totalBudget?.let { "${it}원" } ?: "미설정"}")
                Text("남은 예산: ${state.budgetProgress.totalRemaining?.let { "${it}원" } ?: "-"}")

                CleanField(
                    value = totalBudgetInput,
                    onValueChange = { totalBudgetInput = it.filter(Char::isDigit) },
                    label = "총 예산 설정 (0 입력 시 삭제)",
                    keyboardType = KeyboardType.Number
                )
                Button(onClick = { onSetTotalBudget(totalBudgetInput) }) {
                    Text("총 예산 저장")
                }

                CategorySelectField(
                    value = selectedBudgetCategory,
                    options = categoryBudgetOptions,
                    onValueChange = { selectedBudgetCategory = it },
                    onAddCategory = { selectedBudgetCategory = it },
                    label = "카테고리 예산 대상",
                    triggerTestTag = "options_budget_category_picker_trigger",
                    newCategoryInputTestTag = "options_budget_category_picker_new_input",
                    addCategoryButtonTestTag = "options_budget_category_picker_add"
                )
                if (selectedBudgetCategory.isNotBlank()) {
                    Text(
                        text = "선택된 카테고리: $selectedBudgetCategory",
                        modifier = Modifier.testTag("options_budget_category_selected"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                }
                CleanField(
                    modifier = Modifier.testTag("options_budget_category_amount"),
                    value = categoryBudgetInput,
                    onValueChange = { categoryBudgetInput = it.filter(Char::isDigit) },
                    label = "카테고리 예산 (0 입력 시 삭제)",
                    keyboardType = KeyboardType.Number
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.testTag("options_budget_category_save"),
                        enabled = selectedBudgetCategory.isNotBlank(),
                        onClick = {
                            onSetCategoryBudget(selectedBudgetCategory, categoryBudgetInput)
                            categoryBudgetInput = ""
                        }
                    ) {
                        Text("카테고리 예산 저장")
                    }
                    TextButton(
                        modifier = Modifier.testTag("options_budget_category_delete"),
                        enabled = selectedBudgetCategory.isNotBlank(),
                        onClick = {
                            onRemoveCategoryBudget(selectedBudgetCategory)
                            categoryBudgetInput = ""
                        }
                    ) {
                        Text("선택 예산 삭제", color = Color(0xFFB42318))
                    }
                }

                if (state.budgetProgress.overBudgetMessages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1EC))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            state.budgetProgress.overBudgetMessages.forEach { Text(it, color = Color(0xFF9A3412)) }
                        }
                    }
                }

                if (state.budgetProgress.categoryProgress.isEmpty()) {
                    Text("설정된 카테고리 예산이 없습니다.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                } else {
                    state.budgetProgress.categoryProgress.forEach { progress ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (progress.remaining < 0L) Color(0xFFFFF1EC) else Color(0xFFF8FBFF)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(progress.category, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${progress.used} / ${progress.budget}원",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF475569)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = {
                                        if (progress.budget <= 0L) 0f
                                        else (progress.used.toFloat() / progress.budget.toFloat()).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = if (progress.remaining < 0L) Color(0xFFDC2626) else Color(0xFF2563EB),
                                    trackColor = Color(0xFFDCE7F5)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "남은 예산: ${progress.remaining}원",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (progress.remaining < 0L) Color(0xFFB42318) else Color(0xFF166534)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                selectedBudgetCategory = progress.category
                                                categoryBudgetInput = progress.budget.toString()
                                            }
                                        ) {
                                            Text("불러오기")
                                        }
                                        TextButton(onClick = { onRemoveCategoryBudget(progress.category) }) {
                                            Text("삭제", color = Color(0xFFB42318))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F6FF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("월말 마감/정산", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("이월 자산: ${state.closingPreview.carryIn}원")
                Text("예상 마감: ${state.closingPreview.expectedClosing}원")
                Text("실제 마감: ${state.closingPreview.actualClosing?.let { "${it}원" } ?: "미입력"}")
                Text("차이: ${state.closingPreview.delta?.let { "${it}원" } ?: "-"}")

                CleanField(
                    value = closingActualInput,
                    onValueChange = { closingActualInput = it.filter(Char::isDigit) },
                    label = "실제 총자산 입력",
                    keyboardType = KeyboardType.Number
                )
                Button(onClick = { onCloseMonth(closingActualInput) }) {
                    Text("이번 달 마감 저장")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4F4)),
            border = BorderStroke(1.dp, Color(0xFFFDA4AF))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("기록 초기화", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "거래 기록/달력 메모/월말 마감 기록을 삭제합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F1D1D)
                )

                Button(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("전체 기록 초기화")
                }

                CleanField(
                    value = resetStartDateInput,
                    onValueChange = { resetStartDateInput = it.trim() },
                    label = "시작일 (YYYY-MM-DD, 비워두면 전체 기간)"
                )
                CleanField(
                    value = resetEndDateInput,
                    onValueChange = { resetEndDateInput = it.trim() },
                    label = "종료일 (YYYY-MM-DD, 비워두면 전체 기간)"
                )
                Button(
                    onClick = { showClearPeriodConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("기간 기록 초기화")
                }

                Button(
                    onClick = { showFactoryResetConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("공장초기화 (모든 설정 포함)")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("오분류 검수함 / 룰", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("예: 특정 키워드는 항상 구독/할부/대출로 분류")

                CleanField(
                    value = ruleKeywordInput,
                    onValueChange = { ruleKeywordInput = it },
                    label = "룰 키워드"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SpendingKind.SUBSCRIPTION to "구독",
                        SpendingKind.INSTALLMENT to "할부",
                        SpendingKind.LOAN to "대출",
                        SpendingKind.NORMAL to "일반"
                    ).forEach { (kind, label) ->
                        FilterChip(
                            selected = ruleKind == kind,
                            onClick = { ruleKindName = kind.name },
                            label = { Text(label) }
                        )
                    }
                }
                CleanField(
                    value = ruleCategoryInput,
                    onValueChange = { ruleCategoryInput = it },
                    label = "강제 카테고리 (선택)"
                )
                Button(onClick = { onSaveRule(ruleKeywordInput, ruleKind, ruleCategoryInput) }) {
                    Text("룰 저장")
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FF))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("문장 조건 추가", fontWeight = FontWeight.SemiBold)
                        Text(
                            "예: 토스 내 계좌 이체 건은 소비로 분류",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                        CleanField(
                            value = naturalRuleInput,
                            onValueChange = { naturalRuleInput = it },
                            label = "조건 문장"
                        )
                        Button(
                            onClick = {
                                onSaveNaturalRule(naturalRuleInput)
                                if (naturalRuleInput.isNotBlank()) {
                                    naturalRuleInput = ""
                                }
                            }
                        ) {
                            Text("문장으로 룰 저장")
                        }
                    }
                }

                if (state.classificationRules.isNotEmpty()) {
                    Text("저장된 룰", fontWeight = FontWeight.SemiBold)
                    state.classificationRules.take(12).forEach { rule ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F7FB))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val targetLabel = rule.forcedType?.let(::entryTypeLabel) ?: "기존 타입 유지"
                                    Text(
                                        "${rule.keyword} -> ${spendingKindLabel(rule.spendingKind)} / $targetLabel",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("카테고리: ${rule.category}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { onRemoveRule(rule.id) }) {
                                    Text("삭제", color = Color(0xFFB42318))
                                }
                            }
                        }
                    }
                }

                if (state.reviewCandidates.isNotEmpty()) {
                    Text("검수 권장 항목", fontWeight = FontWeight.SemiBold)
                    state.reviewCandidates.take(8).forEach { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("${entry.description} | ${entry.amount}원", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onCreateRuleFromEntry(entry.id, SpendingKind.SUBSCRIPTION) }) {
                                        Text("구독 룰")
                                    }
                                    TextButton(onClick = { onCreateRuleFromEntry(entry.id, SpendingKind.INSTALLMENT) }) {
                                        Text("할부 룰")
                                    }
                                    TextButton(onClick = { onCreateRuleFromEntry(entry.id, SpendingKind.LOAN) }) {
                                        Text("대출 룰")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("전체 기록 초기화") },
            text = { Text("모든 거래 기록/달력 메모/월말 마감 기록을 삭제합니다. 계속할까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllRecords()
                        showClearAllConfirm = false
                    }
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("취소") }
            }
        )
    }

    if (showClearPeriodConfirm) {
        AlertDialog(
            onDismissRequest = { showClearPeriodConfirm = false },
            title = { Text("기간 기록 초기화") },
            text = {
                Text(
                    "선택 기간의 거래 기록/달력 메모/월말 마감 기록을 삭제합니다.\n" +
                        "시작/종료 값이 비어 있으면 해당 방향 끝까지 적용됩니다.\n" +
                        "기간: ${resetStartDateInput.ifBlank { "(미입력)" }} ~ ${resetEndDateInput.ifBlank { "(미입력)" }}"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRecordsByPeriod(resetStartDateInput, resetEndDateInput)
                        showClearPeriodConfirm = false
                    },
                    enabled = resetStartDateInput.isNotBlank() || resetEndDateInput.isNotBlank()
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPeriodConfirm = false }) { Text("취소") }
            }
        )
    }

    if (showFactoryResetConfirm) {
        AlertDialog(
            onDismissRequest = { showFactoryResetConfirm = false },
            title = { Text("공장초기화") },
            text = {
                Text(
                    "거래/메모/마감뿐 아니라 카테고리, 룰, 템플릿, 예산, 등록 정보, 알림 필터까지 모두 삭제합니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFactoryReset()
                        showFactoryResetConfirm = false
                    }
                ) { Text("초기화") }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetConfirm = false }) { Text("취소") }
            }
        )
    }
}

private fun spendingKindLabel(kind: SpendingKind): String {
    return when (kind) {
        SpendingKind.NORMAL -> "일반"
        SpendingKind.SUBSCRIPTION -> "구독"
        SpendingKind.INSTALLMENT -> "할부"
        SpendingKind.LOAN -> "대출"
    }
}

private fun entryTypeLabel(type: EntryType): String {
    return when (type) {
        EntryType.EXPENSE -> "소비"
        EntryType.INCOME -> "수입"
        EntryType.TRANSFER -> "이체"
    }
}

@Composable
private fun NotificationSourceFilterCard(
    state: HomeUiState,
    onToggleSource: (String, Boolean) -> Unit,
    onToggleAllSources: (Boolean) -> Unit
) {
    if (!state.notificationCaptureSupported) return

    val installedCount = state.notificationSourceOptions.count { it.installed }
    val enabledCount = state.notificationSourceOptions.count { it.installed && it.enabled }
    val totalCount = state.notificationSourceOptions.size

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("알림 수집 앱 필터", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("전체 ${totalCount}개") }
                )
                FilterChip(
                    selected = enabledCount > 0,
                    onClick = { onToggleAllSources(true) },
                    enabled = installedCount > 0 && enabledCount < installedCount,
                    label = { Text("선택됨 ${enabledCount}개") }
                )
                FilterChip(
                    selected = false,
                    onClick = {},
                    enabled = false,
                    label = { Text("설치됨 ${installedCount}개") }
                )
            }
            if (installedCount == 0) {
                Text(
                    "지금 설치된 앱이 없어서 필터를 구성할 수 없습니다. 앱 탭에서 은행 앱을 설치하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309)
                )
            } else if (enabledCount == 0) {
                Text(
                    "현재 알림 수집이 비활성 상태입니다. 최소 1개 앱을 선택하면 자동 수집이 시작됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309)
                )
            } else {
                Text(
                    "선택한 앱의 알림만 수집됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
            if (installedCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { onToggleAllSources(true) }) {
                        Text("전체 선택")
                    }
                    TextButton(onClick = { onToggleAllSources(false) }) {
                        Text("전체 해제")
                    }
                }
            }

            if (state.notificationSourceOptions.isEmpty()) {
                Text(
                    "지원되는 금융 앱 목록을 불러오는 중입니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                state.notificationSourceOptions.forEach { source ->
                    val statusSuffix = if (source.installed) "" else " (미설치)"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${source.label}$statusSuffix",
                            color = if (source.installed) MaterialTheme.colorScheme.onSurface else Color(0xFF94A3B8)
                        )
                        Checkbox(
                            checked = source.enabled,
                            enabled = source.installed,
                            onCheckedChange = { checked ->
                                onToggleSource(source.packageName, checked)
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun NotificationStatusCard(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("알림 연동 상태", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val statusText = when {
                !state.notificationCaptureSupported -> "현재 설치된 앱은 알림 수집 비활성화(SAFE) 버전입니다."
                state.notificationAccessEnabled -> "연결됨: 결제/입출금 알림이 자동 반영됩니다."
                else -> "미연결: 아래 버튼으로 알림 접근에서 MoneyMind를 허용해 주세요."
            }
            Text(statusText)
            Text(
                "거래 알림과 가져온 명세서는 기기에만 저장되며, 기본 상태에서는 외부 서버로 전송되지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF475569)
            )
            if (state.notificationCaptureSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSettings) { Text("알림 접근 권한 열기") }
                    Button(onClick = onRefreshStatus) { Text("상태 새로고침") }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard(
    onOpenPolicy: () -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FC))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("개인정보 및 데이터 처리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "MoneyMind는 거래 알림, 사용자가 선택한 파일, 수동 입력 데이터를 기기 내부 DB에 저장합니다. " +
                    "광고 SDK, 분석 SDK, 외부 동기화 서버는 기본 포함하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF475569)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDialog = true }) {
                    Text("앱 내 요약 보기")
                }
                Button(onClick = onOpenPolicy) {
                    Text("개인정보처리방침 열기")
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("개인정보 및 데이터 처리 요약") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    privacyNoticeSections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(section.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                section.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                    Text(
                        "문서 URL: $privacyPolicyUrl",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }
}
