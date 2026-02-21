package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ExcelStatementParser : StatementParser {
    override fun supports(extension: String): Boolean {
        return extension.lowercase() in setOf("xls", "xlsx")
    }

    override suspend fun parse(context: Context, uri: Uri, sourceName: String): List<ParsedRecord> {
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext emptyList()
            if (bytes.isEmpty()) return@withContext emptyList()

            val excelResult = runCatching { parseWorkbook(bytes) }
            if (excelResult.isSuccess) return@withContext excelResult.getOrThrow()

            val fallback = parseTextSpreadsheet(bytes)
            if (fallback.isNotEmpty()) return@withContext fallback

            throw excelResult.exceptionOrNull() ?: IllegalStateException("Failed to parse workbook")
        }
    }

    private fun parseWorkbook(bytes: ByteArray): List<ParsedRecord> {
        val formatter = DataFormatter()
        val results = mutableListOf<ParsedRecord>()
        val source = EntrySource.EXCEL_IMPORT

        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            if (workbook.numberOfSheets == 0) return emptyList()
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex) ?: continue
                val headerInfo = findWorkbookHeaderRow(sheet.firstRowNum, sheet.lastRowNum) { rowIndex ->
                    val row = sheet.getRow(rowIndex) ?: return@findWorkbookHeaderRow null
                    extractRowValues(row, formatter).takeIf { it.isNotEmpty() }
                } ?: continue

                val headerRowIndex = headerInfo.first
                val headers = normalizeHeaders(headerInfo.second)
                if (headers.isEmpty()) continue

                val startRow = headerRowIndex + 1
                for (rowIndex in startRow..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val values = extractRowValues(row, formatter, minColumns = headers.size)
                    if (values.none { it.isNotBlank() }) continue
                    val alignedValues = alignValuesToHeaders(headers, values)
                    val rowMap = headers.indices.associate { colIndex ->
                        headers[colIndex] to alignedValues.getOrElse(colIndex) { "" }.trim()
                    }
                    RowMapper.mapRow(rowMap, source)?.let(results::add)
                }
            }
        }

        return results
    }

    private fun parseTextSpreadsheet(bytes: ByteArray): List<ParsedRecord> {
        val text = decodeBestEffort(bytes)
        if (text.isBlank()) return emptyList()
        return if (looksLikeHtmlTable(text)) {
            parseHtmlTable(text)
        } else {
            parseDelimitedText(text)
        }
    }

    private fun parseDelimitedText(text: String): List<ParsedRecord> {
        val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
        val firstNonBlankLine = lines.firstOrNull { it.isNotBlank() } ?: return emptyList()
        val headerLineIndex = findDelimitedHeaderLineIndex(lines)
        val trimmedText = lines.drop(headerLineIndex).joinToString("\n")
        val sampleLine = trimmedText.lineSequence().firstOrNull { it.trim().isNotEmpty() } ?: firstNonBlankLine
        val delimiter = detectDelimiter(sampleLine)
        val format = CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .setIgnoreEmptyLines(true)
            .build()

        val source = EntrySource.EXCEL_IMPORT
        val records = mutableListOf<ParsedRecord>()
        format.parse(trimmedText.reader()).forEach { row ->
            val mapped = row.toMap().mapValues { it.value?.trim().orEmpty() }
            RowMapper.mapRow(mapped, source)?.let(records::add)
        }
        return records
    }

    private fun parseHtmlTable(text: String): List<ParsedRecord> {
        val rowRegex = Regex("(?is)<tr[^>]*>(.*?)</tr>")
        val cellRegex = Regex("(?is)<t[dh][^>]*>(.*?)</t[dh]>")
        val tableRows = rowRegex.findAll(text).mapNotNull { rowMatch ->
            val rowText = rowMatch.groupValues[1]
            val cells = cellRegex.findAll(rowText)
                .map { decodeHtmlCell(it.groupValues[1]) }
                .toList()
            cells.takeIf { it.isNotEmpty() }
        }.toList()

        if (tableRows.size < 2) return emptyList()
        val headerRowIndex = findHeaderRowIndex(tableRows)
        val headers = normalizeHeaders(tableRows[headerRowIndex])

        val source = EntrySource.EXCEL_IMPORT
        val records = mutableListOf<ParsedRecord>()
        tableRows.drop(headerRowIndex + 1).forEach { values ->
            if (values.none { it.isNotBlank() }) return@forEach
            val alignedValues = alignValuesToHeaders(headers, values)
            val rowMap = headers.indices.associate { index ->
                headers[index] to alignedValues.getOrElse(index) { "" }.trim()
            }
            RowMapper.mapRow(rowMap, source)?.let(records::add)
        }
        return records
    }

    private fun decodeBestEffort(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        }

        val utf8 = bytes.toString(StandardCharsets.UTF_8)
        val utf8ReplacementCount = utf8.count { it == '\uFFFD' }
        if (utf8ReplacementCount == 0) return utf8

        val ms949 = runCatching { bytes.toString(Charset.forName("MS949")) }.getOrElse { return utf8 }
        val ms949ReplacementCount = ms949.count { it == '\uFFFD' }
        return if (ms949ReplacementCount < utf8ReplacementCount) ms949 else utf8
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

    private fun looksLikeHtmlTable(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("<table") &&
            lower.contains("<tr") &&
            (lower.contains("<td") || lower.contains("<th"))
    }

    private fun decodeHtmlCell(value: String): String {
        var text = value
            .replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)

        text = Regex("&#(\\d+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull()?.let { code -> String(Character.toChars(code)) } ?: match.value
        }
        text = Regex("&#x([0-9a-fA-F]+);").replace(text) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code -> String(Character.toChars(code)) } ?: match.value
        }
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun findWorkbookHeaderRow(
        startRow: Int,
        endRow: Int,
        rowProvider: (Int) -> List<String>?
    ): Pair<Int, List<String>>? {
        if (startRow > endRow) return null

        var fallback: Pair<Int, List<String>>? = null
        var bestScore = Int.MIN_VALUE
        var best: Pair<Int, List<String>>? = null

        val scanEnd = minOf(endRow, startRow + 60)
        for (rowIndex in startRow..scanEnd) {
            val values = rowProvider(rowIndex) ?: continue
            if (values.none { it.isNotBlank() }) continue
            if (fallback == null) fallback = rowIndex to values

            val score = scoreHeader(values)
            if (score > bestScore) {
                bestScore = score
                best = rowIndex to values
            }
        }

        return when {
            best != null && bestScore >= 6 -> best
            else -> fallback
        }
    }

    private fun extractRowValues(row: Row, formatter: DataFormatter, minColumns: Int = 0): List<String> {
        val rowColumns = row.lastCellNum.toInt().coerceAtLeast(0)
        val totalColumns = maxOf(rowColumns, minColumns)
        if (totalColumns <= 0) return emptyList()
        return (0 until totalColumns).map { index ->
            formatter.formatCellValue(row.getCell(index)).trim()
        }
    }

    private fun findDelimitedHeaderLineIndex(lines: List<String>): Int {
        if (lines.isEmpty()) return 0
        var firstNonBlank = 0
        while (firstNonBlank < lines.size && lines[firstNonBlank].isBlank()) {
            firstNonBlank++
        }
        if (firstNonBlank >= lines.size) return 0

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

    private fun findHeaderRowIndex(rows: List<List<String>>): Int {
        if (rows.isEmpty()) return 0
        var bestIndex = 0
        var bestScore = Int.MIN_VALUE
        val scanEnd = minOf(rows.lastIndex, 30)
        for (index in 0..scanEnd) {
            val score = scoreHeader(rows[index])
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return if (bestScore >= 6) bestIndex else 0
    }

    private fun scoreHeader(values: List<String>): Int {
        if (values.isEmpty()) return Int.MIN_VALUE
        val normalized = values.map(::normalizeHeaderToken).filter { it.isNotBlank() }
        if (normalized.isEmpty()) return Int.MIN_VALUE

        val hasDate = normalized.any { cell -> dateHeaderTokens.any { token -> cell.contains(token) } }
        val hasAmount = normalized.any { cell -> amountHeaderTokens.any { token -> cell.contains(token) } }
        val hasDescription = normalized.any { cell -> descriptionHeaderTokens.any { token -> cell.contains(token) } }
        val matchedCount = normalized.count { cell ->
            dateHeaderTokens.any { token -> cell.contains(token) } ||
                amountHeaderTokens.any { token -> cell.contains(token) } ||
                descriptionHeaderTokens.any { token -> cell.contains(token) }
        }
        val nonEmptyCount = normalized.size

        var score = 0
        if (hasDate) score += 4
        if (hasAmount) score += 4
        if (hasDescription) score += 2
        score += matchedCount
        if (nonEmptyCount >= 4) score += 1
        if (nonEmptyCount == 1) score -= 3
        return score
    }

    private fun normalizeHeaders(values: List<String>): List<String> {
        if (values.isEmpty()) return emptyList()
        return values.mapIndexed { index, value ->
            value.trim().ifBlank { "col_$index" }
        }
    }

    private fun normalizeHeaderToken(value: String): String {
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

    private fun alignValuesToHeaders(headers: List<String>, values: List<String>): List<String> {
        if (headers.isEmpty()) return emptyList()
        if (values.size >= headers.size) return values.take(headers.size)

        val aligned = MutableList(headers.size) { "" }
        var headerIndex = 0
        var valueIndex = 0

        while (headerIndex < headers.size && valueIndex < values.size) {
            val remainingHeaders = headers.size - headerIndex
            val remainingValues = values.size - valueIndex
            val normalizedHeader = normalizeHeaderToken(headers[headerIndex])
            val shouldInsertBlank =
                remainingValues < remainingHeaders &&
                    optionalMissingColumnTokens.contains(normalizedHeader)

            if (shouldInsertBlank) {
                headerIndex++
                continue
            }

            aligned[headerIndex] = values[valueIndex]
            headerIndex++
            valueIndex++
        }

        return aligned
    }

    companion object {
        private val dateHeaderTokens = setOf(
            "date",
            "datetime",
            "거래일",
            "거래일자",
            "거래일시",
            "거래시간",
            "사용일",
            "이용일",
            "승인일",
            "승인일시",
            "결제일",
            "매입일",
            "일자",
            "날짜"
        )
        private val descriptionHeaderTokens = setOf(
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
            "상호",
            "상호명",
            "상대명",
            "사용처"
        )
        private val amountHeaderTokens = setOf(
            "amount",
            "total",
            "금액",
            "결제금액",
            "결제원금",
            "청구금액",
            "승인금액",
            "거래금액",
            "이용금액",
            "사용금액",
            "출금",
            "입금"
        )
        private val optionalMissingColumnTokens = setOf(
            "amount",
            "total",
            "금액",
            "거래금액",
            "결제금액",
            "승인금액",
            "이용금액",
            "사용금액",
            "출금",
            "입금"
        ).map(::normalizeToken).toSet()

        private fun normalizeToken(token: String): String {
            return token.lowercase()
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
    }
}
