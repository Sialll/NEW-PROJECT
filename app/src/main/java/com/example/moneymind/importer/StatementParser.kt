package com.example.moneymind.importer

import android.content.Context
import android.net.Uri
import com.example.moneymind.domain.ParsedRecord

interface StatementParser {
    fun supports(extension: String): Boolean
    suspend fun parse(context: Context, uri: Uri, sourceName: String): List<ParsedRecord>
}
