package com.example.moneymind.domain

import java.time.temporal.ChronoUnit

object LedgerFingerprint {
    fun build(entry: LedgerEntry, typeHint: EntryType = entry.type): String {
        val occurredAtKey = entry.occurredAt.truncatedTo(ChronoUnit.SECONDS).toString()
        return listOf(
            occurredAtKey,
            typeHint.name,
            entry.amount.toString(),
            normalizeText(entry.description, maxLength = 80),
            normalizeText(entry.merchant, maxLength = 48),
            normalizeDigits(entry.accountMask),
            normalizeText(entry.counterpartyName, maxLength = 48)
        ).joinToString("|")
    }

    private fun normalizeText(value: String?, maxLength: Int): String {
        return value.orEmpty()
            .trim()
            .lowercase()
            .replace(Regex("[^0-9a-z가-힣]"), "")
            .take(maxLength)
    }

    private fun normalizeDigits(value: String?): String {
        return value.orEmpty().filter(Char::isDigit)
    }
}
