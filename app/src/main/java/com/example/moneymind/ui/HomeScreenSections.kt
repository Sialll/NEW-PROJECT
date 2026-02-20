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
internal fun MainDashboardPage(state: HomeUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpace.l),
        verticalArrangement = Arrangement.spacedBy(UiSpace.m)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF4FF))
            ) {
                Column(
                    modifier = Modifier.padding(UiSpace.m),
                    verticalArrangement = Arrangement.spacedBy(UiSpace.xs)
                ) {
                    ScreenTitle("고정비 집중 뷰")
                    SupportingText("구독/할부/대출을 메인에서 빠르게 확인하고, 일반 가계부는 뒤 화면에서 관리합니다.")
                }
            }
        }
        item { MainChartPanel(state = state) }
        item { RecurringCostCard(summary = state.summary) }
        item { BudgetProgressCard(progress = state.budgetProgress) }
        item { ClosingPreviewCard(preview = state.closingPreview) }
        item { AdvancedReportCard(report = state.report) }
        item { SecurityStatusCard(enabled = state.encryptionEnabled) }

        if (state.warnings.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1EC)),
                    border = BorderStroke(1.dp, Color(0xFFD36A44))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("이번 달 할부 주의", fontWeight = FontWeight.Bold, color = Color(0xFF8B2A14))
                        state.warnings.forEach { Text("- $it", color = Color(0xFF8B2A14)) }
                    }
                }
            }
        }

        if (!state.lastError.isNullOrBlank()) {
            item { Text("오류: ${state.lastError}", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun TopNavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) Color(0xFF2B436B) else Color(0xFF6B7280),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
internal fun LedgerBackPage(
    state: HomeUiState,
    viewMode: LedgerViewMode,
    selectedDayOfMonth: Int,
    checkedEntryIds: Set<String>,
    onViewModeChange: (LedgerViewMode) -> Unit,
    onSelectDay: (Int) -> Unit,
    onToggleChecked: (String) -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onDeleteEntry: (LedgerEntry) -> Unit
) {
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    var keywordFilter by rememberSaveable { mutableStateOf("") }
    var typeFilterName by rememberSaveable { mutableStateOf(EntryTypeFilter.ALL.name) }
    var paymentFilterName by rememberSaveable { mutableStateOf(PaymentMethodFilter.ALL.name) }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var fromDayText by rememberSaveable { mutableStateOf("") }
    var toDayText by rememberSaveable { mutableStateOf("") }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    var highlightedEntryId by rememberSaveable { mutableStateOf<String?>(null) }

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
                                EntryTypeFilter.EXPENSE to "지출",
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "${month.year}년 ${month.monthValue}월 달력",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                weekNames.forEach { dayName ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dayName, color = Color(0xFF5B6470), fontWeight = FontWeight.SemiBold)
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
                                summary = daySummaryMap[day],
                                selected = day == selectedDay,
                                onClick = { onSelectDay(day) }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .height(78.dp)
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
    summary: DayLedgerSummary?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.padding(3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F1FF) else Color(0xFFFFFFFF)
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Color(0xFF5B7CB0) else Color(0xFFE2E8F0)
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(day.toString(), fontWeight = FontWeight.Bold)
            if (summary != null) {
                if (summary.expense > 0L) {
                    Text("-${summary.expense}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB33232))
                }
                if (summary.income > 0L) {
                    Text("+${summary.income}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1C7A4F))
                }
            } else {
                Text("-", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
private fun MainChartPanel(state: HomeUiState) {
    var chartTabName by rememberSaveable { mutableStateOf(MainChartTab.USAGE.name) }
    var chartsExpanded by rememberSaveable { mutableStateOf(true) }
    val chartTab = runCatching { MainChartTab.valueOf(chartTabName) }.getOrDefault(MainChartTab.USAGE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(UiSpace.m),
            verticalArrangement = Arrangement.spacedBy(UiSpace.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("차트 대시보드")
                TextButton(onClick = { chartsExpanded = !chartsExpanded }) {
                    Text(if (chartsExpanded) "차트 접기" else "차트 펼치기")
                }
            }
            SupportingText("차트를 한 화면에 겹쳐보지 않고, 탭으로 분리해서 가독성을 높였습니다.")

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(UiSpace.s)
            ) {
                listOf(
                    MainChartTab.USAGE to "사용 추이",
                    MainChartTab.FLOW_6M to "6개월 흐름",
                    MainChartTab.CATEGORY to "카테고리 비중",
                    MainChartTab.TRIAGRAM to "고정비 삼각"
                ).forEach { (tab, label) ->
                    FilterChip(
                        selected = chartTab == tab,
                        onClick = { chartTabName = tab.name },
                        label = { Text(label) }
                    )
                }
            }

            if (chartsExpanded) {
                when (chartTab) {
                    MainChartTab.USAGE -> BudgetHeroCard(summary = state.summary, entries = state.entries)
                    MainChartTab.FLOW_6M -> MonthlyFlowBarChartCard(entries = state.entries, month = state.summary.month)
                    MainChartTab.CATEGORY -> CategoryShareDonutCard(entries = state.entries, month = state.summary.month)
                    MainChartTab.TRIAGRAM -> TriagramCard(summary = state.summary)
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF111827)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1F2937)
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B)
    )
}

private fun formatWon(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

@Composable
private fun AmountValueText(
    amount: Long,
    tone: AmountTone,
    modifier: Modifier = Modifier,
    showSign: Boolean = false
) {
    val sign = when {
        !showSign -> ""
        tone == AmountTone.INCOME -> "+"
        tone == AmountTone.EXPENSE -> "-"
        else -> ""
    }
    val color = when (tone) {
        AmountTone.INCOME -> Color(0xFF1C7A4F)
        AmountTone.EXPENSE -> Color(0xFFB33232)
        AmountTone.BALANCE -> Color(0xFF0F6A42)
        AmountTone.NEUTRAL -> Color(0xFF334155)
    }
    Text(
        text = "$sign${formatWon(amount)}",
        modifier = modifier,
        color = color,
        fontWeight = FontWeight.Bold
    )
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
    var chartWidthPx by rememberSaveable { mutableStateOf(1f) }

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
    var chartWidthPx by rememberSaveable { mutableStateOf(1f) }

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
                    EntryType.EXPENSE -> "지출"
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
internal fun CleanField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF6C7EA0),
            unfocusedBorderColor = Color(0xFFB8C0CC)
        )
    )
}
@Composable
private fun BudgetHeroCard(summary: MonthlySummary, entries: List<LedgerEntry>) {
    val month = summary.month
    val used = summary.expense
    val baseAmount = if (summary.income > 0L) summary.income else max(summary.expense, 1L)
    val remaining = if (summary.income > 0L) summary.income - summary.expense else 0L
    val progress = (used.toDouble() / baseAmount.toDouble()).toFloat().coerceIn(0f, 1f)
    val chartData = remember(entries, month, summary.income) {
        buildUsageChartData(entries = entries, month = month, income = summary.income)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAFF)),
        border = BorderStroke(1.dp, Color(0xFF7A8DAF))
    ) {
        Column(
            modifier = Modifier.padding(UiSpace.m),
            verticalArrangement = Arrangement.spacedBy(UiSpace.s)
        ) {
            ScreenTitle("${month.year}년 ${month.monthValue}월 사용 현황")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    SupportingText("사용 금액")
                    AmountValueText(amount = used, tone = AmountTone.EXPENSE)
                }
                Column(horizontalAlignment = Alignment.End) {
                    SupportingText("남은 금액")
                    AmountValueText(amount = remaining.coerceAtLeast(0L), tone = AmountTone.BALANCE)
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = Color(0xFFDCE7F5),
                color = Color(0xFF4E6E9E)
            )

            Text(
                if (summary.income > 0L) "기준: 수입 대비 사용률 ${(progress * 100).toInt()}%"
                else "수입 데이터가 없어 사용 금액 중심으로 표시합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4A5568)
            )

            UsageLineChart(
                usedSeries = chartData.usedSeries,
                remainingSeries = chartData.remainingSeries
            )
        }
    }
}

private data class UsageChartData(
    val usedSeries: List<Float>,
    val remainingSeries: List<Float>
)

private data class MonthlyFlowPoint(
    val month: YearMonth,
    val income: Long,
    val expense: Long
)

private data class CategorySlice(
    val category: String,
    val amount: Long,
    val ratio: Float,
    val color: Color
)

private fun buildUsageChartData(entries: List<LedgerEntry>, month: YearMonth, income: Long): UsageChartData {
    val days = month.lengthOfMonth()
    if (days <= 0) return UsageChartData(emptyList(), emptyList())

    val dayExpense = LongArray(days)
    entries.forEach { entry ->
        if (
            entry.type == EntryType.EXPENSE &&
            entry.countedInExpense &&
            YearMonth.from(entry.occurredAt) == month
        ) {
            val index = (entry.occurredAt.dayOfMonth - 1).coerceIn(0, days - 1)
            dayExpense[index] += entry.amount
        }
    }

    var cumulative = 0L
    val usedSeries = MutableList(days) { 0f }
    val remainingSeries = MutableList(days) { 0f }
    for (i in 0 until days) {
        cumulative += dayExpense[i]
        usedSeries[i] = cumulative.toFloat()
        remainingSeries[i] = if (income > 0L) (income - cumulative).coerceAtLeast(0L).toFloat() else 0f
    }

    return UsageChartData(usedSeries = usedSeries, remainingSeries = remainingSeries)
}

private data class MonthlyFlowAccumulator(
    var income: Long = 0L,
    var expense: Long = 0L
)

private fun buildMonthlyFlowPoints(entries: List<LedgerEntry>, endMonth: YearMonth, months: Int = 6): List<MonthlyFlowPoint> {
    if (months <= 0) return emptyList()
    val startMonth = endMonth.minusMonths((months - 1).toLong())
    val monthMap = hashMapOf<YearMonth, MonthlyFlowAccumulator>()

    entries.forEach { entry ->
        val month = YearMonth.from(entry.occurredAt)
        if (month < startMonth || month > endMonth) return@forEach

        val acc = monthMap.getOrPut(month) { MonthlyFlowAccumulator() }
        when (entry.type) {
            EntryType.INCOME -> acc.income += entry.amount
            EntryType.EXPENSE -> if (entry.countedInExpense) acc.expense += entry.amount
            EntryType.TRANSFER -> Unit
        }
    }

    return (months - 1 downTo 0).map { index ->
        val month = endMonth.minusMonths(index.toLong())
        val acc = monthMap[month]
        MonthlyFlowPoint(
            month = month,
            income = acc?.income ?: 0L,
            expense = acc?.expense ?: 0L
        )
    }
}

private fun buildCategorySlices(entries: List<LedgerEntry>, month: YearMonth): List<CategorySlice> {
    val monthExpenses = entries
        .filter {
            it.type == EntryType.EXPENSE &&
                it.countedInExpense &&
                YearMonth.from(it.occurredAt) == month
        }
        .groupBy { it.category.ifBlank { "미분류" } }
        .mapValues { (_, list) -> list.sumOf { it.amount } }

    if (monthExpenses.isEmpty()) return emptyList()

    val sorted = monthExpenses.entries.sortedByDescending { it.value }
    val top = sorted.take(5)
    val other = sorted.drop(5).sumOf { it.value }
    val combined = if (other > 0L) top + listOf(java.util.AbstractMap.SimpleEntry("기타", other)) else top

    val total = combined.sumOf { it.value }.coerceAtLeast(1L)
    val palette = listOf(
        Color(0xFF3B82F6),
        Color(0xFFEF4444),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFF64748B)
    )

    return combined.mapIndexed { index, entry ->
        CategorySlice(
            category = entry.key,
            amount = entry.value,
            ratio = entry.value.toFloat() / total.toFloat(),
            color = palette[index % palette.size]
        )
    }
}

@Composable
private fun MonthlyFlowBarChartCard(entries: List<LedgerEntry>, month: YearMonth) {
    val points = remember(entries, month) { buildMonthlyFlowPoints(entries = entries, endMonth = month) }
    val maxValue = remember(points) { points.maxOfOrNull { max(it.income, it.expense) }?.toFloat()?.coerceAtLeast(1f) ?: 1f }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FBFF)),
        border = BorderStroke(1.dp, Color(0xFF86A7D5))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("6개월 수입/지출 막대차트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                if (points.isEmpty()) return@Canvas

                val clusterWidth = size.width / points.size
                val barWidth = (clusterWidth * 0.28f).coerceAtLeast(8f)

                repeat(4) { step ->
                    val y = size.height * (step / 3f)
                    drawLine(
                        color = Color(0xFFE3EBF6),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                }

                points.forEachIndexed { index, point ->
                    val centerX = clusterWidth * index + (clusterWidth / 2f)
                    val incomeHeight = (point.income.toFloat() / maxValue) * size.height
                    val expenseHeight = (point.expense.toFloat() / maxValue) * size.height

                    drawRect(
                        color = Color(0xFF2F855A),
                        topLeft = Offset(centerX - barWidth - 2f, size.height - incomeHeight),
                        size = Size(barWidth, incomeHeight)
                    )
                    drawRect(
                        color = Color(0xFFC53030),
                        topLeft = Offset(centerX + 2f, size.height - expenseHeight),
                        size = Size(barWidth, expenseHeight)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { point ->
                    Text(
                        "${point.month.monthValue}월",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF475569)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(12.dp)
                            .background(Color(0xFF2F855A), RoundedCornerShape(4.dp))
                    )
                    Text("수입")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .width(12.dp)
                            .background(Color(0xFFC53030), RoundedCornerShape(4.dp))
                    )
                    Text("지출")
                }
            }
        }
    }
}

@Composable
private fun CategoryShareDonutCard(entries: List<LedgerEntry>, month: YearMonth) {
    val slices = remember(entries, month) { buildCategorySlices(entries = entries, month = month) }
    val total = remember(slices) { slices.sumOf { it.amount } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF5)),
        border = BorderStroke(1.dp, Color(0xFFD9B36A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("카테고리 비중 도넛차트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (slices.isEmpty()) {
                Text("이번 달 지출 데이터가 없습니다.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val diameter = minOf(size.width, size.height) * 0.78f
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val sweepBase = 360f

                        var startAngle = -90f
                        slices.forEach { slice ->
                            val sweep = sweepBase * slice.ratio
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = diameter * 0.25f, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("총 지출", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                        Text("${total}원", fontWeight = FontWeight.Bold)
                    }
                }

                slices.forEach { slice ->
                    val percent = (slice.ratio * 100f).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .height(10.dp)
                                    .width(12.dp)
                                    .background(slice.color, RoundedCornerShape(4.dp))
                            )
                            Text(slice.category)
                        }
                        Text("${slice.amount}원 (${percent}%)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageLineChart(usedSeries: List<Float>, remainingSeries: List<Float>) {
    if (usedSeries.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val pointCount = usedSeries.size
            if (pointCount <= 1) return@Canvas

            val maxUsed = usedSeries.maxOrNull() ?: 0f
            val maxRemain = remainingSeries.maxOrNull() ?: 0f
            val maxY = max(1f, max(maxUsed, maxRemain))

            repeat(4) { step ->
                val y = size.height * (step / 3f)
                drawLine(
                    color = Color(0xFFE8EDF4),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            fun toPoint(index: Int, value: Float): Offset {
                val x = (index / (pointCount - 1).toFloat()) * size.width
                val y = size.height - ((value / maxY) * size.height)
                return Offset(x, y)
            }

            val usedPath = Path().apply {
                moveTo(toPoint(0, usedSeries.first()).x, toPoint(0, usedSeries.first()).y)
                for (i in 1 until pointCount) {
                    val pt = toPoint(i, usedSeries[i])
                    lineTo(pt.x, pt.y)
                }
            }

            val remainPath = Path().apply {
                moveTo(toPoint(0, remainingSeries.first()).x, toPoint(0, remainingSeries.first()).y)
                for (i in 1 until pointCount) {
                    val pt = toPoint(i, remainingSeries[i])
                    lineTo(pt.x, pt.y)
                }
            }

            drawPath(
                path = remainPath,
                color = Color(0xFF2F855A),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
            drawPath(
                path = usedPath,
                color = Color(0xFFC53030),
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun TriagramCard(summary: MonthlySummary) {
    val sub = summary.subscriptionExpense.toFloat()
    val inst = summary.installmentExpense.toFloat()
    val loan = summary.loanExpense.toFloat()
    val total = sub + inst + loan

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF8FF)),
        border = BorderStroke(1.dp, Color(0xFF8E7CB5))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("트라이어그램 (구독/할부/대출)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("카드 명세서 import 시 자동 분류된 고정지출 비중입니다.")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val top = Offset(size.width / 2f, 18f)
                    val left = Offset(24f, size.height - 20f)
                    val right = Offset(size.width - 24f, size.height - 20f)

                    drawLine(Color(0xFF8E7CB5), top, left, strokeWidth = 3f)
                    drawLine(Color(0xFF8E7CB5), left, right, strokeWidth = 3f)
                    drawLine(Color(0xFF8E7CB5), right, top, strokeWidth = 3f)

                    drawCircle(Color(0xFF805AD5), radius = 9f, center = top)
                    drawCircle(Color(0xFFE53E3E), radius = 9f, center = left)
                    drawCircle(Color(0xFF2F855A), radius = 9f, center = right)

                    val indicator = if (total > 0f) {
                        Offset(
                            x = (sub * top.x + inst * left.x + loan * right.x) / total,
                            y = (sub * top.y + inst * left.y + loan * right.y) / total
                        )
                    } else {
                        Offset(size.width / 2f, size.height / 2f)
                    }

                    drawCircle(Color(0xFF1A202C), radius = 10f, center = indicator)
                    drawCircle(Color(0xFFFFFFFF), radius = 5f, center = indicator)
                }
            }

            TriagramLegendRow(label = "구독", amount = summary.subscriptionExpense, color = Color(0xFF805AD5), total = total)
            TriagramLegendRow(label = "할부", amount = summary.installmentExpense, color = Color(0xFFE53E3E), total = total)
            TriagramLegendRow(label = "대출", amount = summary.loanExpense, color = Color(0xFF2F855A), total = total)
        }
    }
}

@Composable
private fun TriagramLegendRow(label: String, amount: Long, color: Color, total: Float) {
    val ratio = if (total > 0f) ((amount / total) * 100f).toInt() else 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .fillMaxWidth(0.05f)
                    .background(color, shape = RoundedCornerShape(4.dp))
            )
            Text(label)
        }
        Text("${amount}원 (${ratio}%)", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecurringCostCard(summary: MonthlySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6F6)),
        border = BorderStroke(1.dp, Color(0xFFD85B5B))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("고정/반복 지출 강조", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFB21F1F))
            RecurringRow("구독", summary.subscriptionExpense)
            RecurringRow("할부", summary.installmentExpense)
            RecurringRow("대출", summary.loanExpense)
        }
    }
}

@Composable
private fun RecurringRow(label: String, amount: Long) {
    val rowColor = if (amount > 0L) Color(0xFFFFE2E2) else Color(0xFFF8F8F8)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        AmountValueText(amount = amount, tone = AmountTone.EXPENSE)
    }
}

@Composable
private fun BudgetProgressCard(progress: BudgetProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFBF2)),
        border = BorderStroke(1.dp, Color(0xFF6AA67A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionTitle("예산 진행률")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("지출")
                AmountValueText(amount = progress.totalExpense, tone = AmountTone.EXPENSE)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("총예산")
                Text(progress.totalBudget?.let { "${it}원" } ?: "미설정", fontWeight = FontWeight.Bold, color = Color(0xFF334155))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("남은예산")
                Text(
                    progress.totalRemaining?.let { "${it}원" } ?: "-",
                    fontWeight = FontWeight.Bold,
                    color = if ((progress.totalRemaining ?: 0L) < 0L) Color(0xFFB33232) else Color(0xFF1C7A4F)
                )
            }
            if (progress.overBudgetMessages.isNotEmpty()) {
                progress.overBudgetMessages.forEach { Text(it, color = Color(0xFFB42318)) }
            }
        }
    }
}

@Composable
private fun ClosingPreviewCard(preview: ClosingPreview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9ED)),
        border = BorderStroke(1.dp, Color(0xFFD9A54A))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionTitle("정산/월마감")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("이월 자산")
                AmountValueText(amount = preview.carryIn, tone = AmountTone.BALANCE)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("예상 마감")
                AmountValueText(amount = preview.expectedClosing, tone = AmountTone.NEUTRAL)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("실제 마감")
                Text(preview.actualClosing?.let { "${it}원" } ?: "미입력", fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("차이")
                Text(
                    preview.delta?.let { "${it}원" } ?: "-",
                    fontWeight = FontWeight.Bold,
                    color = when {
                        preview.delta == null -> Color(0xFF334155)
                        preview.delta < 0L -> Color(0xFFB33232)
                        else -> Color(0xFF1C7A4F)
                    }
                )
            }
        }
    }
}

@Composable
private fun AdvancedReportCard(report: AdvancedReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F5FF)),
        border = BorderStroke(1.dp, Color(0xFF6F7FC2))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("심화 리포트", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("이번달 지출: ${formatWon(report.currentExpense)} (전월 ${formatWon(report.previousExpense)})")
            Text("분기 평균 지출: ${formatWon(report.quarterAvgExpense)}")
            Text("이번달 수입: ${formatWon(report.currentIncome)} (전월 ${formatWon(report.previousIncome)})")
            Text("분기 평균 수입: ${formatWon(report.quarterAvgIncome)}")
            Text("최신 총자산 추정: ${report.latestAsset?.let(::formatWon) ?: "-"}")
            Text("고정성 부채성 지출: ${formatWon(report.recurringLiability)}")
            report.topCategoryTrends.forEach { trend ->
                val sign = if (trend.change >= 0) "+" else "-"
                Text("${trend.category}: ${formatWon(trend.currentMonthExpense)} (${sign}${formatWon(kotlin.math.abs(trend.change))})")
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("보안", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (enabled) "민감 텍스트(거래내용/거래처)는 단말 키스토어 기반 암호화 저장 활성화"
                else "암호화 비활성화"
            )
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
                EntryType.EXPENSE -> "지출"
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

internal fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
