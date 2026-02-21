package com.example.moneymind.ui

import android.Manifest
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
import androidx.compose.runtime.mutableIntStateOf
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

internal enum class OptionSection {
    FILE,
    PROFILE,
    CONTROL
}

internal enum class LedgerViewMode {
    CALENDAR,
    CHECKLIST
}

internal enum class EntryTypeFilter {
    ALL,
    INCOME,
    EXPENSE,
    TRANSFER
}

internal enum class PaymentMethodFilter {
    ALL,
    CARD,
    BANK,
    CASH,
    TRANSFER,
    OTHER
}

internal enum class MainChartTab {
    USAGE,
    FLOW_6M,
    CATEGORY,
    TRIAGRAM
}

internal enum class AmountTone {
    INCOME,
    EXPENSE,
    BALANCE,
    NEUTRAL
}

internal object UiSpace {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    var cardLast4Input by rememberSaveable { mutableStateOf("") }
    var installmentMerchantInput by rememberSaveable { mutableStateOf("") }
    var monthlyAmountInput by rememberSaveable { mutableStateOf("") }
    var installmentMonthsInput by rememberSaveable { mutableStateOf("") }

    var manualTypeName by rememberSaveable { mutableStateOf(EntryType.EXPENSE.name) }
    var manualKindName by rememberSaveable { mutableStateOf(SpendingKind.NORMAL.name) }
    var manualAmount by rememberSaveable { mutableStateOf("") }
    var manualDesc by rememberSaveable { mutableStateOf("") }
    var manualMerchant by rememberSaveable { mutableStateOf("") }
    var manualCategory by rememberSaveable { mutableStateOf("") }

    var optionSectionName by rememberSaveable { mutableStateOf(OptionSection.FILE.name) }
    var ledgerViewModeName by rememberSaveable { mutableStateOf(LedgerViewMode.CALENDAR.name) }
    var selectedDayOfMonth by rememberSaveable { mutableIntStateOf(LocalDate.now().dayOfMonth) }
    var checkedEntryIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var pendingExportType by rememberSaveable { mutableStateOf(CsvExportType.ANALYSIS.name) }
    var showSmsPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var smsPromptShownInSession by rememberSaveable { mutableStateOf(false) }

    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editTypeName by rememberSaveable { mutableStateOf(EntryType.EXPENSE.name) }
    var editKindName by rememberSaveable { mutableStateOf(SpendingKind.NORMAL.name) }
    var editAmount by rememberSaveable { mutableStateOf("") }
    var editDescription by rememberSaveable { mutableStateOf("") }
    var editMerchant by rememberSaveable { mutableStateOf("") }
    var editCategory by rememberSaveable { mutableStateOf("") }
    var deleteTargetEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    val manualType = runCatching { EntryType.valueOf(manualTypeName) }.getOrDefault(EntryType.EXPENSE)
    val manualKind = runCatching { SpendingKind.valueOf(manualKindName) }.getOrDefault(SpendingKind.NORMAL)
    val optionSection = runCatching { OptionSection.valueOf(optionSectionName) }.getOrDefault(OptionSection.FILE)
    val ledgerViewMode = runCatching { LedgerViewMode.valueOf(ledgerViewModeName) }
        .getOrDefault(LedgerViewMode.CALENDAR)
    val editType = runCatching { EntryType.valueOf(editTypeName) }.getOrDefault(EntryType.EXPENSE)
    val editKind = runCatching { SpendingKind.valueOf(editKindName) }.getOrDefault(SpendingKind.NORMAL)

    LaunchedEffect(Unit) {
        vm.refreshNotificationAccess()
        vm.refreshSmsPermissionAccess()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshNotificationAccess()
                vm.refreshSmsPermissionAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.smsPermissionGranted, state.notificationCaptureSupported) {
        if (
            !smsPromptShownInSession &&
            state.notificationCaptureSupported &&
            !state.smsPermissionGranted
        ) {
            showSmsPermissionDialog = true
            smsPromptShownInSession = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importStatement(context, it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            val exportType = runCatching { CsvExportType.valueOf(pendingExportType) }
                .getOrDefault(CsvExportType.ANALYSIS)
            vm.exportCsv(context, it, exportType)
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshSmsPermissionAccess()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("나만의 가계부") },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TopNavButton(
                            label = "메인",
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                        )
                        TopNavButton(
                            label = "가계부",
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                        )
                        TopNavButton(
                            label = "옵션",
                            selected = pagerState.currentPage == 2,
                            onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
                        )
                    }
                }
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFFF8F8F5), Color(0xFFEEF1EA)))
                )
        ) { page ->
            when (page) {
                0 -> MainDashboardPage(state = state)
                1 -> LedgerBackPage(
                    state = state,
                    viewMode = ledgerViewMode,
                    selectedDayOfMonth = selectedDayOfMonth,
                    checkedEntryIds = checkedEntryIds,
                    manualType = manualType,
                    manualKind = manualKind,
                    manualAmount = manualAmount,
                    manualDescription = manualDesc,
                    manualMerchant = manualMerchant,
                    manualCategory = manualCategory,
                    categoryOptions = state.categoryOptions,
                    pinnedCategories = state.pinnedCategories,
                    onViewModeChange = { ledgerViewModeName = it.name },
                    onSelectDay = { selectedDayOfMonth = it },
                    onToggleChecked = { id ->
                        checkedEntryIds = if (id in checkedEntryIds) {
                            checkedEntryIds - id
                        } else {
                            checkedEntryIds + id
                        }
                    },
                    onEditEntry = { entry ->
                        editingEntryId = entry.id
                        editTypeName = entry.type.name
                        editKindName = entry.spendingKind.name
                        editAmount = entry.amount.toString()
                        editDescription = entry.description
                        editMerchant = entry.merchant.orEmpty()
                        editCategory = entry.category
                    },
                    onDeleteEntry = { entry ->
                        deleteTargetEntryId = entry.id
                        checkedEntryIds = checkedEntryIds - entry.id
                    },
                    onManualTypeChange = {
                        manualTypeName = it.name
                        if (it != EntryType.EXPENSE) manualKindName = SpendingKind.NORMAL.name
                    },
                    onManualKindChange = { manualKindName = it.name },
                    onManualAmountChange = { manualAmount = it.filter(Char::isDigit) },
                    onManualDescriptionChange = { manualDesc = it },
                    onManualMerchantChange = { manualMerchant = it },
                    onManualCategoryChange = { manualCategory = it },
                    onAddCategoryOption = vm::addCustomCategory,
                    onToggleCategoryPin = vm::togglePinnedCategory,
                    onSaveManual = {
                        vm.addManualEntry(manualType, manualAmount, manualDesc, manualMerchant, manualCategory, manualKind)
                        if (manualAmount.isNotBlank() && manualDesc.isNotBlank()) {
                            manualAmount = ""
                            manualDesc = ""
                            manualMerchant = ""
                            manualKindName = SpendingKind.NORMAL.name
                        }
                    },
                    onLoadManualFromEntry = { entry ->
                        manualTypeName = entry.type.name
                        manualKindName = entry.spendingKind.name
                        manualAmount = entry.amount.toString()
                        manualDesc = entry.description
                        manualMerchant = entry.merchant.orEmpty()
                        manualCategory = entry.category
                    },
                    onSaveTemplate = { name, repeatDayText ->
                        vm.saveQuickTemplate(
                            name = name,
                            type = manualType,
                            amountText = manualAmount,
                            description = manualDesc,
                            merchant = manualMerchant,
                            category = manualCategory,
                            spendingKind = manualKind,
                            repeatMonthlyDayText = repeatDayText
                        )
                    },
                    onRunTemplate = vm::runQuickTemplateNow,
                    onDeleteTemplate = vm::deleteQuickTemplate,
                    onRefreshRecurring = vm::refreshRecurringEntries
                )
                else -> OptionsPage(
                    state = state,
                    optionSection = optionSection,
                    onOptionSectionChange = { optionSectionName = it.name },
                    cardLast4Input = cardLast4Input,
                    installmentMerchantInput = installmentMerchantInput,
                    monthlyAmountInput = monthlyAmountInput,
                    installmentMonthsInput = installmentMonthsInput,
                    onImport = {
                        importLauncher.launch(
                            arrayOf(
                                "text/csv",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/pdf",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onExportBank = {
                        pendingExportType = CsvExportType.BANK_UPLOAD.name
                        exportLauncher.launch("moneymind_bank_${LocalDate.now()}.csv")
                    },
                    onExportAnalysis = {
                        pendingExportType = CsvExportType.ANALYSIS.name
                        exportLauncher.launch("moneymind_analysis_${LocalDate.now()}.csv")
                    },
                    onOpenSettings = { openNotificationListenerSettings(context) },
                    onRefreshNotification = vm::refreshNotificationAccess,
                    onInjectExpenseNotification = {
                        vm.injectTestNotification(HomeViewModel.NotificationTestPreset.EXPENSE_CARD)
                    },
                    onInjectIncomeNotification = {
                        vm.injectTestNotification(HomeViewModel.NotificationTestPreset.INCOME_SALARY)
                    },
                    onInjectLoanNotification = {
                        vm.injectTestNotification(HomeViewModel.NotificationTestPreset.EXPENSE_LOAN)
                    },
                    onCardLast4Change = { cardLast4Input = it.take(24) },
                    onInstallmentMerchantChange = { installmentMerchantInput = it },
                    onMonthlyAmountChange = { monthlyAmountInput = it.filter(Char::isDigit) },
                    onInstallmentMonthsChange = { installmentMonthsInput = it.filter(Char::isDigit) },
                    onSaveInstallment = {
                        vm.registerInstallment(
                            cardLast4 = cardLast4Input,
                            merchant = installmentMerchantInput,
                            monthlyAmount = monthlyAmountInput.toLongOrNull() ?: 0L,
                            totalMonths = installmentMonthsInput.toIntOrNull() ?: 0
                        )
                        cardLast4Input = ""
                        installmentMerchantInput = ""
                        monthlyAmountInput = ""
                        installmentMonthsInput = ""
                    },
                    onSetTotalBudget = vm::setTotalBudget,
                    onCloseMonth = vm::closeCurrentMonth,
                    onSaveRule = vm::saveClassificationRule,
                    onSaveNaturalRule = vm::saveNaturalLanguageRule,
                    onRemoveRule = vm::removeClassificationRule,
                    onCreateRuleFromEntry = vm::createRuleFromEntry
                )
            }
        }
    }

    if (editingEntryId != null) {
        AlertDialog(
            onDismissRequest = { editingEntryId = null },
            title = { Text("거래 수정") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            EntryType.EXPENSE to "소비",
                            EntryType.INCOME to "수입",
                            EntryType.TRANSFER to "이체"
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = editType == type,
                                onClick = {
                                    editTypeName = type.name
                                    if (type != EntryType.EXPENSE) {
                                        editKindName = SpendingKind.NORMAL.name
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (editType == EntryType.EXPENSE) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                SpendingKind.NORMAL to "일반",
                                SpendingKind.SUBSCRIPTION to "구독",
                                SpendingKind.INSTALLMENT to "할부",
                                SpendingKind.LOAN to "대출"
                            ).forEach { (kind, label) ->
                                FilterChip(
                                    selected = editKind == kind,
                                    onClick = { editKindName = kind.name },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    CleanField(
                        value = editAmount,
                        onValueChange = { editAmount = it.filter(Char::isDigit) },
                        label = "금액",
                        keyboardType = KeyboardType.Number
                    )
                    CleanField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = "거래 내용"
                    )
                    CleanField(
                        value = editMerchant,
                        onValueChange = { editMerchant = it },
                        label = "가맹점/거래처 (선택)"
                    )
                    CategorySelectField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        options = state.categoryOptions,
                        onAddCategory = vm::addCustomCategory,
                        label = "카테고리"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingEntryId?.let { id ->
                            vm.updateEntry(
                                entryId = id,
                                type = editType,
                                amountText = editAmount,
                                description = editDescription,
                                merchant = editMerchant,
                                category = editCategory,
                                spendingKind = editKind
                            )
                        }
                        editingEntryId = null
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntryId = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (deleteTargetEntryId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetEntryId = null },
            title = { Text("거래 삭제") },
            text = { Text("선택한 거래를 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTargetEntryId?.let(vm::deleteEntry)
                        if (editingEntryId == deleteTargetEntryId) {
                            editingEntryId = null
                        }
                        deleteTargetEntryId = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetEntryId = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (showSmsPermissionDialog && !state.smsPermissionGranted) {
        AlertDialog(
            onDismissRequest = { showSmsPermissionDialog = false },
            title = { Text("SMS 접근 권한 요청") },
            text = {
                Text(
                    "결제 문자 자동 수집 준비를 위해 SMS 읽기 권한을 요청합니다. " +
                        "거부해도 앱 사용은 가능하고, 알림 수집은 계속 동작합니다."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSmsPermissionDialog = false
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                ) {
                    Text("권한 요청")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsPermissionDialog = false }) {
                    Text("나중에")
                }
            }
        )
    }
}
