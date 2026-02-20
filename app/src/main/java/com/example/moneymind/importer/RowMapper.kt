package com.example.moneymind.importer

import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.absoluteValue

object RowMapper {
    private val locale = Locale.KOREA

    private val dateKeys = listOf(
        "date",
        "datetime",
        "거래일",
        "거래일자",
        "거래일시",
        "사용일",
        "이용일",
        "승인일",
        "승인일시",
        "결제일",
        "매입일",
        "일자",
        "날짜"
    )
    private val descriptionKeys = listOf(
        "description",
        "memo",
        "merchant",
        "detail",
        "적요",
        "내용",
        "가맹점",
        "이용가맹점",
        "거래내용",
        "거래처",
        "사용처",
        "상호명",
        "상대명"
    )
    private val amountKeys = listOf(
        "amount",
        "total",
        "금액",
        "결제금액",
        "결제원금",
        "청구금액",
        "승인금액",
        "거래금액",
        "이용금액",
        "사용금액"
    )
    private val withdrawKeys = listOf(
        "withdraw",
        "debit",
        "출금",
        "출금액",
        "지출",
        "결제원금",
        "결제금액",
        "청구금액",
        "승인금액",
        "이용금액",
        "사용금액"
    )
    private val depositKeys = listOf(
        "deposit",
        "credit",
        "입금",
        "입금액",
        "입금금액",
        "수입",
        "환급금액"
    )
    private val accountKeys = listOf(
        "account",
        "accountno",
        "계좌",
        "계좌번호",
        "카드번호",
        "카드",
        "이용카드",
        "카드명"
    )
    private val fromKeys = listOf(
        "fromaccount", "from", "출금계좌", "보낸계좌", "보낸쪽"
    )
    private val toKeys = listOf(
        "toaccount", "to", "입금계좌", "받는계좌", "받는쪽"
    )
    private val counterpartyKeys = listOf(
        "counterparty", "name", "상대명", "거래상대", "예금주", "보낸분", "받는분", "수취인"
    )

    private val dateTimeFormatters = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy.MM.dd HH:mm:ss",
        "yyyy.MM.dd HH:mm",
        "yyyyMMdd HH:mm:ss",
        "yyyyMMdd HH:mm",
        "yy-MM-dd HH:mm:ss",
        "yy-MM-dd HH:mm",
        "yy/MM/dd HH:mm:ss",
        "yy/MM/dd HH:mm"
    ).map { DateTimeFormatter.ofPattern(it, locale) }

    private val fullDateFormatters = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyy.MM.dd",
        "yyyyMMdd",
        "yy-MM-dd",
        "yy/MM/dd",
        "yy.MM.dd"
    ).map { DateTimeFormatter.ofPattern(it, locale) }

    private val monthDayFormatters = listOf(
        "MM-dd",
        "MM/dd",
        "MM.dd",
        "M-d",
        "M/d",
        "M.d"
    ).map { DateTimeFormatter.ofPattern(it, locale) }

    fun mapRow(row: Map<String, String>, source: EntrySource): ParsedRecord? {
        val normalized = normalizeKeys(row)
        val dateText = findFirst(normalized, dateKeys) ?: return null
        val description = findFirst(normalized, descriptionKeys)
            .orEmpty()
            .ifBlank { "Uncategorized transaction" }

        val withdraw = findFirst(normalized, withdrawKeys)?.let(::parseAmount)
        val deposit = findFirst(normalized, depositKeys)?.let(::parseAmount)
        val amount = findFirst(normalized, amountKeys)?.let(::parseSignedAmount)

        val signed = when {
            withdraw != null && withdraw > 0L -> -withdraw
            deposit != null && deposit > 0L -> deposit
            amount != null && amount != 0L -> amount
            else -> return null
        }

        val occurredAt = parseDate(dateText) ?: return null

        return ParsedRecord(
            occurredAt = occurredAt,
            signedAmount = signed,
            description = description,
            merchant = findFirst(normalized, descriptionKeys),
            accountMask = findFirst(normalized, accountKeys),
            fromAccountMask = findFirst(normalized, fromKeys),
            toAccountMask = findFirst(normalized, toKeys),
            counterpartyName = findFirst(normalized, counterpartyKeys),
            source = source,
            raw = row
        )
    }

    fun parseAmount(text: String): Long {
        return parseSignedAmount(text).absoluteValue
    }

    fun parseSignedAmount(text: String): Long {
        val cleaned = text.trim()
            .replace("\u20A9", "", ignoreCase = true)
            .replace("원", "", ignoreCase = true)
            .replace("krw", "", ignoreCase = true)
            .replace(",", "")
            .replace(Regex("\\s+"), "")

        if (cleaned.isBlank()) return 0L

        val parenthesizedNegative = cleaned.startsWith("(") && cleaned.endsWith(")")
        val explicitNegative = cleaned.startsWith("-") || cleaned.endsWith("-")
        val explicitPositive = cleaned.startsWith("+")

        val digits = cleaned.filter { it.isDigit() }
        val value = digits.toLongOrNull() ?: return 0L

        return when {
            parenthesizedNegative || explicitNegative -> -value
            explicitPositive -> value
            else -> value
        }
    }

    fun parseDate(text: String, baseDate: LocalDate = LocalDate.now()): LocalDateTime? {
        val sanitized = normalizeDateText(text)

        dateTimeFormatters.forEach { formatter ->
            try {
                return LocalDateTime.parse(sanitized, formatter)
            } catch (_: DateTimeParseException) {
                // try next formatter
            }
        }

        fullDateFormatters.forEach { formatter ->
            try {
                return LocalDate.parse(sanitized, formatter).atStartOfDay()
            } catch (_: DateTimeParseException) {
                // try next formatter
            }
        }

        monthDayFormatters.forEach { formatter ->
            try {
                val monthDay = MonthDay.parse(sanitized, formatter)
                return inferYear(monthDay, baseDate).atStartOfDay()
            } catch (_: DateTimeParseException) {
                // try next formatter
            }
        }

        return null
    }

    private fun normalizeDateText(text: String): String {
        return text.trim()
            .replace("년", "-")
            .replace("월", "-")
            .replace("일", "")
            .replace('/', '-')
            .replace('.', '-')
            .replace(Regex("\\s*-\\s*"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeKeys(row: Map<String, String>): Map<String, String> {
        return row.mapKeys { (key, _) ->
            key.lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("/", "")
                .replace(".", "")
                .replace(":", "")
                .replace("(", "")
                .replace(")", "")
        }
    }

    private fun findFirst(row: Map<String, String>, keys: List<String>): String? {
        return keys.firstNotNullOfOrNull { key ->
            val normalizedKey = key.lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("/", "")
                .replace(".", "")
                .replace(":", "")
                .replace("(", "")
                .replace(")", "")
            row[normalizedKey]?.takeIf { it.isNotBlank() }
        }
    }

    private fun inferYear(monthDay: MonthDay, baseDate: LocalDate): LocalDate {
        var candidate = monthDay.atYear(baseDate.year)
        if (candidate.isAfter(baseDate.plusDays(45))) {
            candidate = candidate.minusYears(1)
        } else if (candidate.isBefore(baseDate.minusDays(320))) {
            candidate = candidate.plusYears(1)
        }
        return candidate
    }
}
