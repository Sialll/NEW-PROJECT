package com.example.moneymind.domain

class InternalTransferDetector {
    private val transferKeywords = listOf(
        "\uC774\uCCB4",
        "\uACC4\uC88C\uC774\uB3D9",
        "\uC1A1\uAE08",
        "\uC790\uCCB4\uC774\uCCB4",
        "\uBCF8\uC778\uACC4\uC88C",
        "transfer"
    )

    fun isInternalTransfer(
        record: ParsedRecord,
        ownedAccounts: List<OwnedAccount>,
        ownerAliases: Set<String>
    ): Boolean {
        val normalizedAliases = (ownerAliases + ownedAccounts.map { it.ownerName })
            .map(::normalize)
            .filter { it.isNotBlank() }
            .toSet()

        val description = normalize(record.description)
        val hasTransferKeyword = transferKeywords.any {
            description.contains(normalize(it))
        }

        val normalizedOwnedMasks = ownedAccounts.map { normalizeMask(it.accountMask) }
            .filter { it.isNotBlank() }
            .toSet()

        val fromOwned = normalizeMask(record.fromAccountMask).let { it.isNotBlank() && it in normalizedOwnedMasks }
        val toOwned = normalizeMask(record.toAccountMask).let { it.isNotBlank() && it in normalizedOwnedMasks }
        if (fromOwned && toOwned) return true

        val directAccountOwned = normalizeMask(record.accountMask).let { it.isNotBlank() && it in normalizedOwnedMasks }
        val counterparty = normalize(record.counterpartyName.orEmpty())
        if (hasTransferKeyword && directAccountOwned && counterparty in normalizedAliases) {
            return true
        }

        if (hasTransferKeyword && normalizedAliases.any { alias ->
            alias.isNotBlank() && description.contains(alias)
        }) {
            return true
        }

        return false
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .trim()
    }

    private fun normalizeMask(value: String?): String {
        return value.orEmpty().filter { it.isDigit() }
    }
}
