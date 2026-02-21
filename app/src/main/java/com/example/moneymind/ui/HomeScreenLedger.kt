package com.example.moneymind.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
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
internal fun LedgerBackPage(
    state: HomeUiState,
    viewMode: LedgerViewMode,
    selectedDayOfMonth: Int,
    checkedEntryIds: Set<String>,
    manualType: EntryType,
    manualKind: SpendingKind,
    manualAmount: String,
    manualDescription: String,
    manualMerchant: String,
    manualCategory: String,
    categoryOptions: List<String>,
    pinnedCategories: Set<String>,
    onViewModeChange: (LedgerViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleChecked: (String) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onDeleteEntry: (LedgerEntry) -> Unit,
    onManualTypeChange: (EntryType) -> Unit,
    onManualKindChange: (SpendingKind) -> Unit,
    onManualAmountChange: (String) -> Unit,
    onManualDescriptionChange: (String) -> Unit,
    onManualMerchantChange: (String) -> Unit,
    onManualCategoryChange: (String) -> Unit,
    onAddCategoryOption: (String) -> Unit,
    onToggleCategoryPin: (String) -> Unit,
    onSaveManual: () -> Unit,
    onLoadManualFromEntry: (LedgerEntry) -> Unit,
    onSaveTemplate: (String, String) -> Unit,
    onRunTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onRefreshRecurring: () -> Unit
) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var keywordFilter by rememberSaveable { mutableStateOf("") }
    var typeFilterName by rememberSaveable { mutableStateOf(EntryTypeFilter.ALL.name) }
    var paymentFilterName by rememberSaveable { mutableStateOf(PaymentMethodFilter.ALL.name) }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var fromDayText by rememberSaveable { mutableStateOf("") }
    var toDayText by rememberSaveable { mutableStateOf("") }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var highlightedEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var addCategoryText by rememberSaveable { mutableStateOf("") }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val listScope = rememberCoroutineScope()
    val checklistSectionIndex = 3
    val selectedDaySectionIndex = 4

    val month = YearMonth.now().plusMonths(monthOffset.toLong())
    val typeFilter = runCatching { EntryTypeFilter.valueOf(typeFilterName) }.getOrDefault(EntryTypeFilter.ALL)
    val paymentFilter = runCatching { PaymentMethodFilter.valueOf(paymentFilterName) }
        .getOrDefault(PaymentMethodFilter.ALL)
    val fromDay = fromDayText.toIntOrNull()?.coerceIn(1, month.lengthOfMonth())
    val toDay = toDayText.toIntOrNull()?.coerceIn(1, month.lengthOfMonth())
    val normalizedKeyword = keywordFilter.trim().lowercase()
    val normalizedCategory = categoryFilter.trim()

    val monthEntriesBase by remember(
        state.entries,
        month,
        fromDay,
        toDay,
        normalizedKeyword,
        typeFilter,
        paymentFilter
    ) {
        derivedStateOf {
            state.entries
                .asSequence()
                .filter { YearMonth.from(it.occurredAt) == month }
                .filter { entry ->
                    if (fromDay != null && entry.occurredAt.dayOfMonth < fromDay) return@filter false
                    if (toDay != null && entry.occurredAt.dayOfMonth > toDay) return@filter false
                    true
                }
                .filter { entry ->
                    if (normalizedKeyword.isBlank()) return@filter true
                    val merged = "${entry.description.lowercase()} ${entry.merchant.orEmpty().lowercase()} ${entry.category.lowercase()}"
                    merged.contains(normalizedKeyword)
                }
                .filter { entry -> matchesTypeFilter(entry, typeFilter) }
                .filter { entry -> matchesPaymentFilter(entry, paymentFilter) }
                .sortedByDescending { it.occurredAt }
                .toList()
        }
    }

    val monthEntries by remember(monthEntriesBase, normalizedCategory) {
        derivedStateOf {
            monthEntriesBase.filter { entry ->
                normalizedCategory.isBlank() || entry.category == normalizedCategory
            }
        }
    }

    val categories by remember(monthEntriesBase) {
        derivedStateOf {
            monthEntriesBase
                .asSequence()
                .map { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .toList()
        }
    }

    val daySummaryMap by remember(monthEntries) {
        derivedStateOf { buildDaySummaryMap(monthEntries) }
    }
    val selectedDayEntries by remember(monthEntries, selectedDayOfMonth) {
        derivedStateOf {
            monthEntries
                .filter { it.occurredAt.dayOfMonth == selectedDayOfMonth }
                .sortedByDescending { it.occurredAt }
        }
    }

    LaunchedEffect(monthEntries, selectedDayEntries) {
        val highlighted = highlightedEntryId ?: return@LaunchedEffect
        if (monthEntries.none { it.id == highlighted } && selectedDayEntries.none { it.id == highlighted }) {
            highlightedEntryId = null
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpace.l),
        verticalArrangement = Arrangement.spacedBy(UiSpace.m)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F7F4))
            ) {
                Column(
                    modifier = Modifier.padding(UiSpace.m),
                    verticalArrangement = Arrangement.spacedBy(UiSpace.s)
                ) {
                    ScreenTitle("일반 가계부")
                    SupportingText("달력식과 체크리스트 방식 중 원하는 보기로 확인하세요.")
                    SupportingText(
                        if (manualCategory.isBlank()) {
                            "현재 입력 카테고리: 미선택"
                        } else {
                            "현재 입력 카테고리: $manualCategory"
                        }
                    )
                    val allCategoryOptions = remember(state.entries, categoryOptions, manualCategory, pinnedCategories) {
                        (state.entries.map { it.category } + categoryOptions + listOf(manualCategory))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sortedBy { it }
                    }
                    val recentCategoryOptions = remember(state.entries, categoryOptions, manualCategory, pinnedCategories) {
                        val recentFromEntries = state.entries
                            .sortedByDescending { it.occurredAt }
                            .asSequence()
                            .map { it.category.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .toList()

                        (recentFromEntries + categoryOptions + listOf(manualCategory))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(5)
                    }
                    val pinnedOptionList = remember(allCategoryOptions, pinnedCategories) {
                        allCategoryOptions.filter { it in pinnedCategories }
                    }
                    val displayedCategoryOptions = remember(
                        showAllCategories,
                        allCategoryOptions,
                        recentCategoryOptions,
                        pinnedOptionList
                    ) {
                        if (showAllCategories) {
                            pinnedOptionList + allCategoryOptions.filterNot { it in pinnedCategories }
                        } else {
                            if (pinnedOptionList.size >= 5) {
                                pinnedOptionList
                            } else {
                                pinnedOptionList + recentCategoryOptions
                                    .filterNot { it in pinnedCategories }
                                    .take(5 - pinnedOptionList.size)
                            }
                        }.distinct()
                    }
                    if (displayedCategoryOptions.isEmpty()) {
                        SupportingText("카테고리 없음: 아래에서 직접 추가해 주세요.")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                            ) {
                                SupportingText(
                                    if (showAllCategories) "카테고리 전체" else "최근 카테고리 5개 (고정 우선)"
                                )
                                if (allCategoryOptions.size > 5) {
                                    TextButton(onClick = { showAllCategories = !showAllCategories }) {
                                    Text(if (showAllCategories) "접기" else "전체 보기")
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayedCategoryOptions.forEach { category ->
                                FilterChip(
                                    selected = manualCategory == category,
                                    onClick = { onManualCategoryChange(category) },
                                    label = { Text(if (category in pinnedCategories) "★ $category" else category) }
                                )
                            }
                        }
                        if (manualCategory.isNotBlank()) {
                            TextButton(
                                onClick = { onToggleCategoryPin(manualCategory) }
                            ) {
                                Text(
                                    if (manualCategory in pinnedCategories) {
                                        "현재 카테고리 고정 해제"
                                    } else {
                                        "현재 카테고리 고정"
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = addCategoryText,
                            onValueChange = { addCategoryText = it.take(20) },
                            label = { Text("카테고리 직접 추가") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val normalized = addCategoryText.trim()
                                if (normalized.isBlank()) return@Button
                                onAddCategoryOption(normalized)
                                onManualCategoryChange(normalized)
                                addCategoryText = ""
                            }
                        ) {
                            Text("추가")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { monthOffset -= 1 }) { Text("이전달") }
                        Text("${month.year}년 ${month.monthValue}월", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { monthOffset += 1 }) { Text("다음달") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = viewMode == LedgerViewMode.CALENDAR,
                            onClick = { onViewModeChange(LedgerViewMode.CALENDAR) },
                            label = { Text("달력식") }
                        )
                        FilterChip(
                            selected = viewMode == LedgerViewMode.CHECKLIST,
                            onClick = { onViewModeChange(LedgerViewMode.CHECKLIST) },
                            label = { Text("체크리스트") }
                        )
                    }
                    CleanField(
                        value = keywordFilter,
                        onValueChange = { keywordFilter = it },
                        label = "검색 (키워드)"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SupportingText("필터 결과: ${monthEntries.size}건")
                        TextButton(onClick = { showAdvancedFilters = !showAdvancedFilters }) {
                            Text(if (showAdvancedFilters) "고급 필터 숨기기" else "고급 필터 열기")
                        }
                    }
                    if (showAdvancedFilters) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                EntryTypeFilter.ALL to "전체",
                                EntryTypeFilter.EXPENSE to "소비",
                                EntryTypeFilter.INCOME to "수입",
                                EntryTypeFilter.TRANSFER to "이체"
                            ).forEach { (filter, label) ->
                                FilterChip(
                                    selected = typeFilter == filter,
                                    onClick = { typeFilterName = filter.name },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                PaymentMethodFilter.ALL to "결제수단 전체",
                                PaymentMethodFilter.CARD to "카드",
                                PaymentMethodFilter.BANK to "계좌",
                                PaymentMethodFilter.CASH to "현금",
                                PaymentMethodFilter.TRANSFER to "이체"
                            ).forEach { (filter, label) ->
                                FilterChip(
                                    selected = paymentFilter == filter,
                                    onClick = { paymentFilterName = filter.name },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (categories.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = normalizedCategory.isBlank(),
                                    onClick = { categoryFilter = "" },
                                    label = { Text("카테고리 전체") }
                                )
                                categories.forEach { category ->
                                    FilterChip(
                                        selected = normalizedCategory == category,
                                        onClick = { categoryFilter = category },
                                        label = { Text(category) }
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = fromDayText,
                                onValueChange = { fromDayText = it.filter(Char::isDigit).take(2) },
                                label = { Text("시작일") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = toDayText,
                                onValueChange = { toDayText = it.filter(Char::isDigit).take(2) },
                                label = { Text("종료일") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }

        item {
            ManualEntrySection(
                state = state,
                manualType = manualType,
                manualKind = manualKind,
                amount = manualAmount,
                description = manualDescription,
                merchant = manualMerchant,
                category = manualCategory,
                onTypeChange = onManualTypeChange,
                onKindChange = onManualKindChange,
                onAmountChange = onManualAmountChange,
                onDescriptionChange = onManualDescriptionChange,
                onMerchantChange = onManualMerchantChange,
                onSave = onSaveManual,
                onLoadManualFromEntry = onLoadManualFromEntry,
                onSaveTemplate = onSaveTemplate,
                onRunTemplate = onRunTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onRefreshRecurring = onRefreshRecurring
            )
        }

        item {
            DailyExpenseJumpChartCard(
                month = month,
                daySummaryMap = daySummaryMap,
                selectedDay = selectedDayOfMonth.coerceIn(1, month.lengthOfMonth()),
                onSelectDay = { day ->
                    highlightedEntryId = monthEntries.firstOrNull { it.occurredAt.dayOfMonth == day }?.id
                        ?: monthEntriesBase.firstOrNull { it.occurredAt.dayOfMonth == day }?.id
                    onSelectDay(day)
                    onViewModeChange(LedgerViewMode.CALENDAR)
                    listScope.launch {
                        delay(140)
                        listState.animateScrollToItem(selectedDaySectionIndex)
                    }
                }
            )
        }

        item {
            CategoryJumpBarChartCard(
                entries = monthEntriesBase,
                selectedCategory = normalizedCategory,
                onSelectCategory = { category ->
                    categoryFilter = category
                    highlightedEntryId = monthEntriesBase.firstOrNull { it.category == category }?.id
                    onViewModeChange(LedgerViewMode.CHECKLIST)
                    listScope.launch {
                        delay(140)
                        listState.animateScrollToItem(checklistSectionIndex)
                    }
                }
            )
        }

        if (viewMode == LedgerViewMode.CALENDAR) {
            item {
                CalendarLedgerSection(
                    month = month,
                    daySummaryMap = daySummaryMap,
                    selectedDay = selectedDayOfMonth.coerceIn(1, month.lengthOfMonth()),
                    onSelectDay = onSelectDay
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${selectedDayOfMonth.coerceIn(1, month.lengthOfMonth())}일 거래",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedDayEntries.isEmpty()) {
                            Text("선택한 날짜의 거래가 없습니다.")
                        } else {
                            selectedDayEntries.forEach { entry ->
                                EntryRow(
                                    entry = entry,
                                    onEdit = { onEditEntry(entry) },
                                    onDelete = { onDeleteEntry(entry) },
                                    highlighted = entry.id == highlightedEntryId
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${month.year}년 ${month.monthValue}월 체크리스트",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (monthEntries.isEmpty()) {
                            Text("이번 달 거래가 없습니다.")
                        } else {
                            monthEntries.forEach { entry ->
                                ChecklistEntryRow(
                                    entry = entry,
                                    checked = entry.id in checkedEntryIds,
                                    onToggle = { onToggleChecked(entry.id) },
                                    onEdit = { onEditEntry(entry) },
                                    onDelete = { onDeleteEntry(entry) },
                                    highlighted = entry.id == highlightedEntryId
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLedgerSection(
    month: YearMonth,
    daySummaryMap: Map<Int, DayLedgerSummary>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    val firstDayOffset = month.atDay(1).dayOfWeek.value % 7
    val totalCells = ((firstDayOffset + daysInMonth + 6) / 7) * 7
    val weekNames = listOf("일", "월", "화", "수", "목", "금", "토")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "${month.year}년 ${month.monthValue}월 달력",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                weekNames.forEachIndexed { index, dayName ->
                    val dayColor = when (index) {
                        0 -> Color(0xFFD97706)
                        6 -> Color(0xFF64748B)
                        else -> Color(0xFF6B7280)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dayName, color = dayColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            for (week in 0 until totalCells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (indexInWeek in 0 until 7) {
                        val index = week * 7 + indexInWeek
                        val day = index - firstDayOffset + 1
                        if (day in 1..daysInMonth) {
                            CalendarDayCell(
                                modifier = Modifier.weight(1f),
                                day = day,
                                weekDayIndex = indexInWeek,
                                summary = daySummaryMap[day],
                                selected = day == selectedDay,
                                onClick = { onSelectDay(day) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(2.dp)
                                    .height(88.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    day: Int,
    weekDayIndex: Int,
    summary: DayLedgerSummary?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val dayNumberColor = when (weekDayIndex) {
        0 -> Color(0xFFD97706)
        6 -> Color(0xFF334155)
        else -> Color(0xFF111827)
    }
    Card(
        modifier = modifier.padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F2FF) else Color(0xFFFFFFFF)
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Color(0xFF5B7CB0) else Color(0xFFD5DBE3)
        ),
        onClick = onClick
    ) {
        val amountLine = remember(summary) { buildCalendarAmountLine(summary) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(day.toString(), fontWeight = FontWeight.ExtraBold, color = dayNumberColor)
            Text(
                text = amountLine.text,
                style = MaterialTheme.typography.labelSmall,
                color = amountLine.color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class CalendarAmountLine(
    val text: String,
    val color: Color
)

private fun buildCalendarAmountLine(summary: DayLedgerSummary?): CalendarAmountLine {
    if (summary == null) {
        return CalendarAmountLine(
            text = "-",
            color = Color(0xFF94A3B8)
        )
    }

    val hasExpense = summary.expense > 0L
    val hasIncome = summary.income > 0L

    return when {
        hasExpense && hasIncome -> CalendarAmountLine(
            text = "소비 ${formatCalendarAmount(summary.expense)} · 수입 ${formatCalendarAmount(summary.income)}",
            color = Color(0xFF334155)
        )

        hasExpense -> CalendarAmountLine(
            text = "소비 ${formatCalendarAmount(summary.expense)}",
            color = Color(0xFFB33232)
        )

        hasIncome -> CalendarAmountLine(
            text = "수입 ${formatCalendarAmount(summary.income)}",
            color = Color(0xFF1C7A4F)
        )

        else -> CalendarAmountLine(
            text = "-",
            color = Color(0xFF94A3B8)
        )
    }
}


@Composable
private fun DailyExpenseJumpChartCard(
    month: YearMonth,
    daySummaryMap: Map<Int, DayLedgerSummary>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit
) {
    val days = month.lengthOfMonth().coerceAtLeast(1)
    val expenseSeries = (1..days).map { day -> daySummaryMap[day]?.expense ?: 0L }
    val maxExpense = expenseSeries.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    var chartWidthPx by rememberSaveable { mutableFloatStateOf(1f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6FAFF)),
        border = BorderStroke(1.dp, Color(0xFF7DA0D7))
    ) {
        Column(
            modifier = Modifier.padding(UiSpace.m),
            verticalArrangement = Arrangement.spacedBy(UiSpace.s)
        ) {
            SectionTitle("일별 지출 라인차트")
            SupportingText("차트 터치 시 해당 날짜로 바로 이동합니다.")

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(days, chartWidthPx) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / chartWidthPx).coerceIn(0f, 1f)
                            val day = ((ratio * (days - 1)).roundToInt() + 1).coerceIn(1, days)
                            onSelectDay(day)
                        }
                    }
            ) {
                if (days <= 1) return@Canvas

                repeat(4) { step ->
                    val y = size.height * (step / 3f)
                    drawLine(
                        color = Color(0xFFE3EBF6),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                fun dayPoint(day: Int): Offset {
                    val index = day - 1
                    val x = (index / (days - 1).toFloat()) * size.width
                    val value = expenseSeries[index].toFloat()
                    val y = size.height - ((value / maxExpense) * size.height)
                    return Offset(x, y)
                }

                val path = Path().apply {
                    val first = dayPoint(1)
                    moveTo(first.x, first.y)
                    for (day in 2..days) {
                        val point = dayPoint(day)
                        lineTo(point.x, point.y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFFC53030),
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )

                val selectedPoint = dayPoint(selectedDay.coerceIn(1, days))
                drawCircle(Color(0xFF1E293B), radius = 8f, center = selectedPoint)
                drawCircle(Color(0xFFFFFFFF), radius = 4f, center = selectedPoint)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1일", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                Text("${days / 2}일", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                Text("${days}일", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun CategoryJumpBarChartCard(
    entries: List<LedgerEntry>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val points = remember(entries) { buildCategoryExpenseChartPoints(entries) }
    val maxAmount = remember(points) { points.maxOfOrNull { it.amount }?.toFloat()?.coerceAtLeast(1f) ?: 1f }
    var chartWidthPx by rememberSaveable { mutableFloatStateOf(1f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF4)),
        border = BorderStroke(1.dp, Color(0xFFD6AE65))
    ) {
        Column(
            modifier = Modifier.padding(UiSpace.m),
            verticalArrangement = Arrangement.spacedBy(UiSpace.s)
        ) {
            SectionTitle("카테고리 지출 막대차트")
            SupportingText("막대 터치 시 해당 카테고리로 바로 필터됩니다.")

            if (points.isEmpty()) {
                Text("표시할 지출 데이터가 없습니다.")
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(points.size, chartWidthPx) {
                            detectTapGestures { offset ->
                                val cluster = chartWidthPx / points.size
                                val index = (offset.x / cluster).toInt().coerceIn(0, points.lastIndex)
                                onSelectCategory(points[index].category)
                            }
                        }
                ) {
                    val cluster = size.width / points.size
                    val barWidth = (cluster * 0.56f).coerceAtLeast(12f)

                    drawLine(
                        color = Color(0xFFE5E7EB),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2f
                    )

                    points.forEachIndexed { index, point ->
                        val barHeight = (point.amount.toFloat() / maxAmount) * size.height
                        val left = cluster * index + (cluster - barWidth) / 2f
                        val top = size.height - barHeight

                        drawRect(
                            color = if (point.category == selectedCategory) point.color else point.color.copy(alpha = 0.75f),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight)
                        )

                        if (point.category == selectedCategory) {
                            drawRect(
                                color = Color(0xFF1E293B),
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { point ->
                        val label = if (point.category.length > 4) {
                            point.category.take(4) + "…"
                        } else {
                            point.category
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistEntryRow(
    entry: LedgerEntry,
    checked: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    highlighted: Boolean = false
) {
    val baseColor = if (checked) Color(0xFFEFFBF2) else Color(0xFFF9FAFB)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFFFFF5D8) else baseColor
        ),
        border = if (highlighted) BorderStroke(1.dp, Color(0xFFD97706)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val date = entry.occurredAt.format(DateTimeFormatter.ofPattern("MM-dd"))
                val typeLabel = when (entry.type) {
                    EntryType.INCOME -> "수입"
                    EntryType.EXPENSE -> "소비"
                    EntryType.TRANSFER -> "이체"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$date | $typeLabel", fontWeight = FontWeight.SemiBold)
                    AmountValueText(
                        amount = entry.amount,
                        tone = when (entry.type) {
                            EntryType.INCOME -> AmountTone.INCOME
                            EntryType.EXPENSE -> AmountTone.EXPENSE
                            EntryType.TRANSFER -> AmountTone.NEUTRAL
                        },
                        showSign = entry.type != EntryType.TRANSFER
                    )
                }
                Text(entry.description, style = MaterialTheme.typography.bodySmall)
                if (highlighted) {
                    Text("차트에서 선택됨", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEdit) { Text("수정") }
                    TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFB42318)) }
                }
            }
        }
    }
}

private data class DayLedgerSummary(
    val income: Long = 0L,
    val expense: Long = 0L
)

private data class CategoryExpenseChartPoint(
    val category: String,
    val amount: Long,
    val color: Color
)

private fun formatCalendarAmount(amount: Long): String {
    return String.format(Locale.KOREA, "%,d", amount)
}

private fun buildDaySummaryMap(entries: List<LedgerEntry>): Map<Int, DayLedgerSummary> {
    val map = mutableMapOf<Int, DayLedgerSummary>()
    entries.forEach { entry ->
        val day = entry.occurredAt.dayOfMonth
        val previous = map[day] ?: DayLedgerSummary()
        val updated = when (entry.type) {
            EntryType.INCOME -> previous.copy(income = previous.income + entry.amount)
            EntryType.EXPENSE -> if (entry.countedInExpense) {
                previous.copy(expense = previous.expense + entry.amount)
            } else {
                previous
            }
            EntryType.TRANSFER -> previous
        }
        map[day] = updated
    }
    return map
}

private fun buildCategoryExpenseChartPoints(entries: List<LedgerEntry>): List<CategoryExpenseChartPoint> {
    val grouped = entries
        .asSequence()
        .filter { it.type == EntryType.EXPENSE && it.countedInExpense }
        .groupBy { it.category.ifBlank { "미분류" } }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .entries
        .sortedByDescending { it.value }

    if (grouped.isEmpty()) return emptyList()

    val top = grouped.take(5)
    val other = grouped.drop(5).sumOf { it.value }
    val points = if (other > 0L) top + listOf(java.util.AbstractMap.SimpleEntry("기타", other)) else top
    val palette = listOf(
        Color(0xFF2563EB),
        Color(0xFFDC2626),
        Color(0xFF16A34A),
        Color(0xFFF59E0B),
        Color(0xFF7C3AED),
        Color(0xFF475569)
    )

    return points.mapIndexed { index, entry ->
        CategoryExpenseChartPoint(
            category = entry.key,
            amount = entry.value,
            color = palette[index % palette.size]
        )
    }
}


@Composable
private fun EntryRow(
    entry: LedgerEntry,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    highlighted: Boolean = false
) {
    val baseColor = when (entry.type) {
        EntryType.INCOME -> Color(0xFFEDF8EE)
        EntryType.EXPENSE -> Color(0xFFFFF4F2)
        EntryType.TRANSFER -> Color(0xFFF0F4FF)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) Color(0xFFFFF5D8) else baseColor
        ),
        border = if (highlighted) BorderStroke(1.dp, Color(0xFFD97706)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val typeLabel = when (entry.type) {
                EntryType.INCOME -> "수입"
                EntryType.EXPENSE -> "소비"
                EntryType.TRANSFER -> "이체"
            }
            val date = entry.occurredAt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$date | $typeLabel", fontWeight = FontWeight.SemiBold)
                AmountValueText(
                    amount = entry.amount,
                    tone = when (entry.type) {
                        EntryType.INCOME -> AmountTone.INCOME
                        EntryType.EXPENSE -> AmountTone.EXPENSE
                        EntryType.TRANSFER -> AmountTone.NEUTRAL
                    },
                    showSign = entry.type != EntryType.TRANSFER
                )
            }
            Text(entry.description)
            Text("분류: ${entry.category}")
            if (highlighted) {
                Text("차트에서 선택됨", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309))
            }
            if (onEdit != null && onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onEdit) { Text("수정") }
                    TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFB42318)) }
                }
            }
        }
    }
}

private fun matchesTypeFilter(entry: LedgerEntry, filter: EntryTypeFilter): Boolean {
    return when (filter) {
        EntryTypeFilter.ALL -> true
        EntryTypeFilter.INCOME -> entry.type == EntryType.INCOME
        EntryTypeFilter.EXPENSE -> entry.type == EntryType.EXPENSE
        EntryTypeFilter.TRANSFER -> entry.type == EntryType.TRANSFER
    }
}

private fun matchesPaymentFilter(entry: LedgerEntry, filter: PaymentMethodFilter): Boolean {
    val inferred = inferPaymentMethod(entry)
    return when (filter) {
        PaymentMethodFilter.ALL -> true
        PaymentMethodFilter.CARD -> inferred == PaymentMethodFilter.CARD
        PaymentMethodFilter.BANK -> inferred == PaymentMethodFilter.BANK
        PaymentMethodFilter.CASH -> inferred == PaymentMethodFilter.CASH
        PaymentMethodFilter.TRANSFER -> inferred == PaymentMethodFilter.TRANSFER
        PaymentMethodFilter.OTHER -> inferred == PaymentMethodFilter.OTHER
    }
}

private fun inferPaymentMethod(entry: LedgerEntry): PaymentMethodFilter {
    if (entry.type == EntryType.TRANSFER) return PaymentMethodFilter.TRANSFER

    val merged = "${entry.description.lowercase()} ${entry.merchant.orEmpty().lowercase()}"
    return when {
        merged.contains("현금") || merged.contains("cash") -> PaymentMethodFilter.CASH
        merged.contains("카드") || merged.contains("card") || entry.spendingKind == SpendingKind.INSTALLMENT -> PaymentMethodFilter.CARD
        entry.accountMask?.isNotBlank() == true -> PaymentMethodFilter.BANK
        else -> PaymentMethodFilter.OTHER
    }
}
