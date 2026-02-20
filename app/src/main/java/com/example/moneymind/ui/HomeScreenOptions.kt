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
    manualType: EntryType,
    manualKind: SpendingKind,
    amount: String,
    description: String,
    merchant: String,
    category: String,
    aliasInput: String,
    bankInput: String,
    accountInput: String,
    ownerInput: String,
    cardLast4Input: String,
    installmentMerchantInput: String,
    monthlyAmountInput: String,
    installmentMonthsInput: String,
    onImport: () -> Unit,
    onExportBank: () -> Unit,
    onExportAnalysis: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshNotification: () -> Unit,
    onInjectExpenseNotification: () -> Unit,
    onInjectIncomeNotification: () -> Unit,
    onInjectLoanNotification: () -> Unit,
    onTypeChange: (EntryType) -> Unit,
    onKindChange: (SpendingKind) -> Unit,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSaveManual: () -> Unit,
    onAliasChange: (String) -> Unit,
    onBankChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onOwnerChange: (String) -> Unit,
    onCardLast4Change: (String) -> Unit,
    onInstallmentMerchantChange: (String) -> Unit,
    onMonthlyAmountChange: (String) -> Unit,
    onInstallmentMonthsChange: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onSaveAccount: () -> Unit,
    onSaveInstallment: () -> Unit,
    onLoadManualFromEntry: (LedgerEntry) -> Unit,
    onSaveTemplate: (String, String) -> Unit,
    onRunTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRefreshRecurring: () -> Unit,
    onSetTotalBudget: (String) -> Unit,
    onSetCategoryBudget: (String, String) -> Unit,
    onRemoveCategoryBudget: (String) -> Unit,
    onCloseMonth: (String) -> Unit,
    onSaveRule: (String, SpendingKind, String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onCreateRuleFromEntry: (String, SpendingKind) -> Unit
) {
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
                    Text("파일/입력/등록 기능은 옵션에서 관리합니다.")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = optionSection == OptionSection.FILE,
                            onClick = { onOptionSectionChange(OptionSection.FILE) },
                            label = { Text("파일") }
                        )
                        FilterChip(
                            selected = optionSection == OptionSection.MANUAL,
                            onClick = { onOptionSectionChange(OptionSection.MANUAL) },
                            label = { Text("수동 입력") }
                        )
                        FilterChip(
                            selected = optionSection == OptionSection.PROFILE,
                            onClick = { onOptionSectionChange(OptionSection.PROFILE) },
                            label = { Text("등록 관리") }
                        )
                        FilterChip(
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
                    FileOptionSection(
                        onImport = onImport,
                        onExportBank = onExportBank,
                        onExportAnalysis = onExportAnalysis,
                        onInjectExpenseNotification = onInjectExpenseNotification,
                        onInjectIncomeNotification = onInjectIncomeNotification,
                        onInjectLoanNotification = onInjectLoanNotification
                    )
                }
            }
            OptionSection.MANUAL -> {
                item {
                    ManualOptionSection(
                        state = state,
                        manualType = manualType,
                        manualKind = manualKind,
                        amount = amount,
                        description = description,
                        merchant = merchant,
                        category = category,
                        onTypeChange = onTypeChange,
                        onKindChange = onKindChange,
                        onAmountChange = onAmountChange,
                        onDescriptionChange = onDescriptionChange,
                        onMerchantChange = onMerchantChange,
                        onCategoryChange = onCategoryChange,
                        onSave = onSaveManual,
                        onLoadManualFromEntry = onLoadManualFromEntry,
                        onSaveTemplate = onSaveTemplate,
                        onRunTemplate = onRunTemplate,
                        onDeleteTemplate = onDeleteTemplate,
                        onRefreshRecurring = onRefreshRecurring
                    )
                }
            }

            OptionSection.PROFILE -> {
                item {
                    ProfileOptionSection(
                        state = state,
                        aliasInput = aliasInput,
                        bankInput = bankInput,
                        accountInput = accountInput,
                        ownerInput = ownerInput,
                        cardLast4Input = cardLast4Input,
                        installmentMerchantInput = installmentMerchantInput,
                        monthlyAmountInput = monthlyAmountInput,
                        installmentMonthsInput = installmentMonthsInput,
                        onAliasChange = onAliasChange,
                        onBankChange = onBankChange,
                        onAccountChange = onAccountChange,
                        onOwnerChange = onOwnerChange,
                        onCardLast4Change = onCardLast4Change,
                        onInstallmentMerchantChange = onInstallmentMerchantChange,
                        onMonthlyAmountChange = onMonthlyAmountChange,
                        onInstallmentMonthsChange = onInstallmentMonthsChange,
                        onSaveAlias = onSaveAlias,
                        onSaveAccount = onSaveAccount,
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
                        onSaveRule = onSaveRule,
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
    }
}

@Composable
private fun ManualOptionSection(
    state: HomeUiState,
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
    onCategoryChange: (String) -> Unit,
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
                    listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
                        FilterChip(
                            selected = manualType == type,
                            onClick = { onTypeChange(type) },
                            label = { Text(if (type == EntryType.EXPENSE) "지출" else "수입") }
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
                CleanField(
                    value = category,
                    onValueChange = onCategoryChange,
                    label = "카테고리 (비우면 자동)"
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
                        CleanField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = "템플릿 이름 (비우면 거래내용 사용)"
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
    state: HomeUiState,
    aliasInput: String,
    bankInput: String,
    accountInput: String,
    ownerInput: String,
    cardLast4Input: String,
    installmentMerchantInput: String,
    monthlyAmountInput: String,
    installmentMonthsInput: String,
    onAliasChange: (String) -> Unit,
    onBankChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onOwnerChange: (String) -> Unit,
    onCardLast4Change: (String) -> Unit,
    onInstallmentMerchantChange: (String) -> Unit,
    onMonthlyAmountChange: (String) -> Unit,
    onInstallmentMonthsChange: (String) -> Unit,
    onSaveAlias: () -> Unit,
    onSaveAccount: () -> Unit,
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
                Text("대출/할부는 카드 명세서 import 시 자동 인식됩니다.")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("본인 별칭 등록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CleanField(value = aliasInput, onValueChange = onAliasChange, label = "별칭 (예: 내이름)")
                Button(onClick = onSaveAlias) { Text("별칭 저장") }
                if (state.ownerAliases.isNotEmpty()) {
                    Text("등록된 별칭: ${state.ownerAliases.joinToString(", ")}")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("내 계좌 등록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CleanField(value = bankInput, onValueChange = onBankChange, label = "은행")
                CleanField(value = accountInput, onValueChange = onAccountChange, label = "계좌 마스킹/끝자리")
                CleanField(value = ownerInput, onValueChange = onOwnerChange, label = "예금주명")
                Button(onClick = onSaveAccount) { Text("계좌 저장") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("할부 수동 등록 (옵션)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("자동 인식이 누락될 때만 사용하세요.")
                CleanField(value = cardLast4Input, onValueChange = onCardLast4Change, label = "카드 끝 4자리")
                CleanField(value = installmentMerchantInput, onValueChange = onInstallmentMerchantChange, label = "가맹점")
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
                Button(onClick = onSaveInstallment) { Text("할부 저장") }
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
    onSaveRule: (String, SpendingKind, String) -> Unit,
    onRemoveRule: (String) -> Unit,
    onCreateRuleFromEntry: (String, SpendingKind) -> Unit
) {
    var totalBudgetInput by rememberSaveable { mutableStateOf("") }
    var budgetCategoryInput by rememberSaveable { mutableStateOf("") }
    var categoryBudgetInput by rememberSaveable { mutableStateOf("") }
    var closingActualInput by rememberSaveable { mutableStateOf("") }
    var ruleKeywordInput by rememberSaveable { mutableStateOf("") }
    var ruleCategoryInput by rememberSaveable { mutableStateOf("") }
    var ruleKindName by rememberSaveable { mutableStateOf(SpendingKind.SUBSCRIPTION.name) }
    val ruleKind = runCatching { SpendingKind.valueOf(ruleKindName) }.getOrDefault(SpendingKind.SUBSCRIPTION)

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

                CleanField(
                    value = budgetCategoryInput,
                    onValueChange = { budgetCategoryInput = it },
                    label = "카테고리"
                )
                CleanField(
                    value = categoryBudgetInput,
                    onValueChange = { categoryBudgetInput = it.filter(Char::isDigit) },
                    label = "카테고리 예산",
                    keyboardType = KeyboardType.Number
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSetCategoryBudget(budgetCategoryInput, categoryBudgetInput) }) {
                        Text("카테고리 예산 저장")
                    }
                    Button(onClick = { onRemoveCategoryBudget(budgetCategoryInput) }) {
                        Text("삭제")
                    }
                }

                state.budgetProgress.categoryProgress.take(10).forEach { progress ->
                    val color = if (progress.remaining < 0L) Color(0xFFB42318) else Color(0xFF1C7A4F)
                    Text(
                        "${progress.category}: ${progress.used}/${progress.budget} (잔여 ${progress.remaining})",
                        color = color
                    )
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
                                    Text("${rule.keyword} -> ${rule.spendingKind.name}", fontWeight = FontWeight.SemiBold)
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
            if (state.notificationCaptureSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSettings) { Text("알림 접근 권한 열기") }
                    Button(onClick = onRefreshStatus) { Text("상태 새로고침") }
                }
            }
        }
    }
}

