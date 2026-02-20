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

