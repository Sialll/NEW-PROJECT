package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat

class CsvStatementParser : StatementParser {
    override fun supports(extension: String): Boolean {
        return extension.lowercase() == "csv"
    }

    override suspend fun parse(context: Context, uri: Uri, sourceName: String): List<ParsedRecord> {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader().use { it.readText() }.removePrefix("\uFEFF")
                if (text.isBlank()) return@use emptyList()

                val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
                val headerLineIndex = findHeaderLineIndex(lines)
                val trimmedText = lines.drop(headerLineIndex).joinToString("\n")
                if (trimmedText.isBlank()) return@use emptyList()

                val firstLine = trimmedText.lineSequence().firstOrNull { it.isNotBlank() } ?: return@use emptyList()
                val delimiter = detectDelimiter(firstLine)
                val format = CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setIgnoreEmptyLines(true)
                    .build()

                val source = EntrySource.CSV_IMPORT
                val records = mutableListOf<ParsedRecord>()
                format.parse(trimmedText.reader()).forEach { csvRecord ->
                    val row = csvRecord.toMap().mapValues { it.value?.trim().orEmpty() }
                    RowMapper.mapRow(row, source)?.let(records::add)
                }
                records
            } ?: emptyList()
        }
    }

    private fun findHeaderLineIndex(lines: List<String>): Int {
        if (lines.isEmpty()) return 0
        val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }.takeIf { it >= 0 } ?: return 0

        var bestIndex = firstNonBlank
        var bestScore = Int.MIN_VALUE
        val scanEnd = minOf(lines.lastIndex, firstNonBlank + 30)

        for (index in firstNonBlank..scanEnd) {
            val line = lines[index]
            if (line.isBlank()) continue
            val delimiter = detectDelimiter(line)
            val values = line.split(delimiter).map { it.trim() }
            val score = scoreHeader(values)
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return if (bestScore >= 6) bestIndex else firstNonBlank
    }

    private fun detectDelimiter(line: String): Char {
        val tabCount = line.count { it == '\t' }
        val commaCount = line.count { it == ',' }
        val semicolonCount = line.count { it == ';' }
        return when {
            tabCount >= commaCount && tabCount >= semicolonCount && tabCount > 0 -> '\t'
            semicolonCount > commaCount -> ';'
            else -> ','
        }
    }

    private fun scoreHeader(values: List<String>): Int {
        val normalized = values.map(::normalizeToken).filter { it.isNotBlank() }
        if (normalized.isEmpty()) return Int.MIN_VALUE

        val hasDate = normalized.any { cell -> dateHeaderTokens.any { token -> cell.contains(token) } }
        val hasAmount = normalized.any { cell -> amountHeaderTokens.any { token -> cell.contains(token) } }
        val hasDescription = normalized.any { cell -> descriptionHeaderTokens.any { token -> cell.contains(token) } }
        val matchedCount = normalized.count { cell ->
            dateHeaderTokens.any { token -> cell.contains(token) } ||
                amountHeaderTokens.any { token -> cell.contains(token) } ||
                descriptionHeaderTokens.any { token -> cell.contains(token) }
        }

        var score = 0
        if (hasDate) score += 4
        if (hasAmount) score += 4
        if (hasDescription) score += 2
        score += matchedCount
        if (normalized.size >= 4) score += 1
        if (normalized.size == 1) score -= 3
        return score
    }

    private fun normalizeToken(value: String): String {
        return value.trim().lowercase()
            .replace("\uFEFF", "")
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .replace("/", "")
            .replace(".", "")
            .replace(":", "")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "")
            .replace("]", "")
    }

    companion object {
        private val dateHeaderTokens = setOf(
            "date", "datetime", "거래일", "거래일자", "거래일시",
            "사용일", "이용일", "승인일", "승인일시", "결제일", "매입일", "일자", "날짜"
        )
        private val descriptionHeaderTokens = setOf(
            "description", "memo", "merchant", "detail", "적요",
            "내용", "가맹점", "이용가맹점", "거래내용", "거래처", "상호", "상대명"
        )
        private val amountHeaderTokens = setOf(
            "amount", "total", "금액", "결제금액", "결제원금", "청구금액",
            "승인금액", "거래금액", "이용금액", "사용금액", "출금", "입금"
        )
    }
}
