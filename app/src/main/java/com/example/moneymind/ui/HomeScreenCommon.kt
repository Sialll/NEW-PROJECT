package com.example.moneymind.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
internal fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF111827)
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF1F2937)
    )
}

@Composable
internal fun SupportingText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B)
    )
}

internal fun formatWon(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

@Composable
internal fun AmountValueText(
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
internal fun CategorySelectField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    label: String = "카테고리"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var addMode by rememberSaveable { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf("") }

    val normalizedOptions = remember(options) {
        options.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                value = value,
                onValueChange = {},
                label = { Text(label) },
                placeholder = { Text("카테고리 선택") },
                trailingIcon = { Text(if (expanded) "▲" else "▼") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6C7EA0),
                    unfocusedBorderColor = Color(0xFFB8C0CC)
                )
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("자동 분류") },
                    onClick = {
                        onValueChange("")
                        expanded = false
                        addMode = false
                    }
                )

                if (normalizedOptions.isNotEmpty()) {
                    HorizontalDivider()
                }

                normalizedOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                            addMode = false
                        }
                    )
                }

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("+ 카테고리 추가") },
                    onClick = {
                        addMode = true
                        expanded = false
                    }
                )
            }
        }

        if (addMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = newCategory,
                    onValueChange = { newCategory = it.take(30) },
                    label = { Text("새 카테고리") },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        val normalized = newCategory.trim()
                        if (normalized.isBlank()) return@TextButton
                        onAddCategory(normalized)
                        onValueChange(normalized)
                        newCategory = ""
                        addMode = false
                    }
                ) {
                    Text("추가")
                }
            }
        }
    }
}

internal fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
