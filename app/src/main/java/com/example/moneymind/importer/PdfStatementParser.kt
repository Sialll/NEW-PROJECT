package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfStatementParser : StatementParser {
    private val linePattern = Regex(
        pattern = "(\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}|\\d{1,2}[./-]\\d{1,2})\\s+(.+?)\\s+([+-]?\\d[\\d,]*)\\s*(원|KRW)?$",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    override fun supports(extension: String): Boolean {
        return extension.lowercase() in setOf("pdf", "ppf")
    }

    override suspend fun parse(context: Context, uri: Uri, sourceName: String): List<ParsedRecord> {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val document = PDDocument.load(input)
                document.use { pdf ->
                    val text = PDFTextStripper().getText(pdf)
                    text.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .mapNotNull { parseLine(it) }
                        .toList()
                }
            } ?: emptyList()
        }
    }

    private fun parseLine(line: String): ParsedRecord? {
        val match = linePattern.find(line) ?: return null
        val dateText = match.groupValues[1]
        val description = match.groupValues[2]
        val amountText = match.groupValues[3]

        val occurredAt = RowMapper.parseDate(dateText) ?: return null
        val parsedAmount = RowMapper.parseSignedAmount(amountText)
        if (parsedAmount == 0L) return null

        val lower = description.lowercase()
        val isIncomeHint = listOf("입금", "수입", "환급", "급여").any { lower.contains(it) }
        val signedAmount = if (amountText.trim().startsWith("-")) {
            parsedAmount
        } else if (amountText.trim().startsWith("+")) {
            parsedAmount
        } else {
            if (isIncomeHint) parsedAmount else -parsedAmount
        }

        return ParsedRecord(
            occurredAt = occurredAt,
            signedAmount = signedAmount,
            description = description,
            merchant = description,
            source = EntrySource.PDF_IMPORT,
            raw = mapOf("line" to line)
        )
    }
}
