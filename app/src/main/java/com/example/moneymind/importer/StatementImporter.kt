package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.ParsedRecord

class StatementImporter(
    private val parsers: List<StatementParser> = listOf(
        CsvStatementParser(),
        ExcelStatementParser(),
        PdfStatementParser()
    )
) {
    suspend fun import(context: Context, uri: Uri): List<ParsedRecord> {
        val name = FileNameResolver.displayName(context, uri)
        val extension = detectFormat(context, uri, name)

        val parser = parsers.firstOrNull { it.supports(extension) }
            ?: error("Unsupported file format: .$extension (supported: csv, xls, xlsx, pdf, ppf)")

        return parser.parse(context, uri, name)
    }

    private fun detectFormat(context: Context, uri: Uri, fileName: String): String {
        val fromName = fileName.substringAfterLast('.', "").lowercase()
        if (fromName in supportedExtensions) return fromName

        val fromMime = context.contentResolver.getType(uri)
            ?.trim()
            ?.lowercase()
            ?.let(::mimeToExtension)
        if (fromMime != null) return fromMime

        val fromContent = detectByContentHeader(context, uri)
        if (fromContent != null) return fromContent

        return fromName
    }

    private fun mimeToExtension(mimeType: String): String? {
        return when (mimeType) {
            "text/csv", "application/csv", "text/plain" -> "csv"
            "application/pdf" -> "pdf"
            "application/vnd.ms-excel" -> "xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-excel.sheet.macroenabled.12" -> "xlsx"
            "application/vnd.ms-excel.sheet.binary.macroenabled.12" -> "xls"
            else -> null
        }
    }

    private fun detectByContentHeader(context: Context, uri: Uri): String? {
        val header = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8_192)
            val read = input.read(buffer)
            if (read <= 0) return@use ByteArray(0)
            buffer.copyOf(read)
        } ?: return null

        if (header.isEmpty()) return null
        if (startsWith(header, pdfSignature)) return "pdf"
        if (startsWith(header, zipSignature)) return "xlsx"
        if (startsWith(header, oleSignature)) return "xls"
        if (looksLikeCsv(header)) return "csv"
        return null
    }

    private fun startsWith(data: ByteArray, signature: ByteArray): Boolean {
        if (data.size < signature.size) return false
        return signature.indices.all { index -> data[index] == signature[index] }
    }

    private fun looksLikeCsv(data: ByteArray): Boolean {
        val text = data.decodeToString()
        val hasComma = text.contains(',') || text.contains('\t')
        val hasLineBreak = text.contains('\n') || text.contains('\r')
        return hasComma && hasLineBreak
    }

    companion object {
        private val supportedExtensions = setOf("csv", "xls", "xlsx", "pdf", "ppf")
        private val pdfSignature = "%PDF".encodeToByteArray()
        private val zipSignature = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val oleSignature = byteArrayOf(
            0xD0.toByte(),
            0xCF.toByte(),
            0x11.toByte(),
            0xE0.toByte(),
            0xA1.toByte(),
            0xB1.toByte(),
            0x1A.toByte(),
            0xE1.toByte()
        )
    }
}
