package com.example.moneymind.export

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.LedgerEntry
import com.example.moneymind.domain.EntryType
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

enum class CsvExportType {
    BANK_UPLOAD,
    ANALYSIS
}

class CsvLedgerExporter {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    suspend fun export(
        context: Context,
        uri: Uri,
        entries: List<LedgerEntry>,
        exportType: CsvExportType
    ) {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("CSV 파일을 열 수 없습니다.")

            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                CSVPrinter(writer, format(exportType)).use { csv ->
                    entries.sortedByDescending { it.occurredAt }.forEach { entry ->
                        when (exportType) {
                            CsvExportType.BANK_UPLOAD -> {
                                val (inAmount, outAmount, transferAmount) = when (entry.type) {
                                    EntryType.INCOME -> Triple(entry.amount.toString(), "", "")
                                    EntryType.EXPENSE -> Triple("", entry.amount.toString(), "")
                                    EntryType.TRANSFER -> Triple("", "", entry.amount.toString())
                                }
                                csv.printRecord(
                                    entry.occurredAt.format(dateFormatter),
                                    inAmount,
                                    outAmount,
                                    transferAmount,
                                    entry.description,
                                    entry.merchant.orEmpty(),
                                    entry.accountMask.orEmpty(),
                                    entry.counterpartyName.orEmpty(),
                                    entry.type.name
                                )
                            }
                            CsvExportType.ANALYSIS -> {
                                csv.printRecord(
                                    entry.occurredAt.format(dateFormatter),
                                    entry.type.name,
                                    entry.amount,
                                    entry.category,
                                    entry.description,
                                    entry.merchant.orEmpty(),
                                    entry.spendingKind.name,
                                    entry.countedInExpense,
                                    entry.source.name,
                                    entry.accountMask.orEmpty(),
                                    entry.counterpartyName.orEmpty()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private val bankUploadFormat = CSVFormat.DEFAULT.builder()
        .setHeader(
            "transaction_at",
            "in_amount",
            "out_amount",
            "transfer_amount",
            "description",
            "merchant",
            "account_mask",
            "counterparty_name",
            "type"
        )
        .build()

    private val analysisFormat = CSVFormat.DEFAULT.builder()
        .setHeader(
            "occurred_at",
            "type",
            "amount",
            "category",
            "description",
            "merchant",
            "spending_kind",
            "counted_in_expense",
            "source",
            "account_mask",
            "counterparty_name"
        )
        .build()

    private fun format(type: CsvExportType): CSVFormat {
        return when (type) {
            CsvExportType.BANK_UPLOAD -> bankUploadFormat
            CsvExportType.ANALYSIS -> analysisFormat
        }
    }
}
