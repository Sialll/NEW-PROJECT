package com.example.moneymind.notification

import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import java.time.LocalDateTime
import kotlin.math.absoluteValue

object NotificationMessageParser {
    private val unitAfterAmount = Regex("([+-]?\\d[\\d,]{0,14})\\s*(원|krw)", RegexOption.IGNORE_CASE)
    private val unitBeforeAmount = Regex("(원|krw)\\s*([+-]?\\d[\\d,]{0,14})", RegexOption.IGNORE_CASE)
    private val keywordBeforeAmount = Regex(
        "(결제|입금|출금|사용|승인|이체|송금|환급|급여|payment|paid|deposit|withdraw|transfer)\\D{0,8}([+-]?\\d[\\d,]{0,14})",
        RegexOption.IGNORE_CASE
    )
    private val keywordAfterAmount = Regex(
        "([+-]?\\d[\\d,]{0,14})\\D{0,8}(결제|입금|출금|사용|승인|이체|송금|환급|급여|payment|paid|deposit|withdraw|transfer)",
        RegexOption.IGNORE_CASE
    )

    private val incomeHints = listOf("입금", "수입", "환급", "급여", "salary", "deposit", "refund")
    private val expenseHints = listOf("결제", "승인", "출금", "사용", "purchase", "payment", "withdraw")
    private val transactionHints = listOf(
        "결제", "승인", "입금", "출금", "이체", "송금", "환급", "급여",
        "payment", "deposit", "withdraw", "transfer", "purchase", "approved"
    )
    private val amountBoostHints = listOf(
        "결제", "승인", "출금", "입금", "이체", "송금", "환급", "급여",
        "payment", "deposit", "withdraw", "transfer"
    )
    private val amountPenaltyHints = listOf(
        "잔액", "시도", "포인트", "적립", "쿠폰", "혜택", "광고", "이벤트",
        "balance", "point", "coupon", "promo", "event"
    )

    fun parse(title: String?, text: String?): ParsedRecord? {
        if (!looksLikeTransaction(title, text)) return null

        val merged = listOfNotNull(title, text).joinToString(" ").trim()
        if (merged.isBlank()) return null

        val amountInfo = extractAmount(merged) ?: return null
        val lower = merged.lowercase()

        val signed = when {
            amountInfo.explicitSign < 0 -> -amountInfo.amount
            amountInfo.explicitSign > 0 -> amountInfo.amount
            incomeHints.any { lower.contains(it) } -> amountInfo.amount
            expenseHints.any { lower.contains(it) } -> -amountInfo.amount
            else -> -amountInfo.amount
        }

        return ParsedRecord(
            occurredAt = LocalDateTime.now(),
            signedAmount = signed,
            description = merged,
            merchant = title,
            source = EntrySource.NOTIFICATION,
            raw = mapOf(
                "title" to (title ?: ""),
                "text" to (text ?: "")
            )
        )
    }

    fun looksLikeTransaction(title: String?, text: String?): Boolean {
        val merged = listOfNotNull(title, text).joinToString(" ").trim()
        if (merged.isBlank()) return false

        val lower = merged.lowercase()
        if (amountPenaltyHints.count { lower.contains(it) } >= 2 &&
            !transactionHints.any { lower.contains(it) }
        ) {
            return false
        }

        val amountInfo = extractAmount(merged) ?: return false
        val hasHint = transactionHints.any { lower.contains(it) }
        val hasUnit = lower.contains("원") || lower.contains("krw")

        return (hasHint || hasUnit || amountInfo.explicitSign != 0) && amountInfo.amount > 0L
    }

    private fun extractAmount(text: String): AmountCandidate? {
        val candidates = mutableListOf<AmountCandidate>()

        unitAfterAmount.findAll(text).forEach { match ->
            val token = match.groupValues[1]
            parseAmountToken(token)?.let { amount ->
                candidates.add(
                    AmountCandidate(
                        amount = amount,
                        explicitSign = detectSign(token),
                        score = 60 + scoreContext(text, match.range)
                    )
                )
            }
        }
        unitBeforeAmount.findAll(text).forEach { match ->
            val token = match.groupValues[2]
            parseAmountToken(token)?.let { amount ->
                candidates.add(
                    AmountCandidate(
                        amount = amount,
                        explicitSign = detectSign(token),
                        score = 60 + scoreContext(text, match.range)
                    )
                )
            }
        }
        keywordBeforeAmount.findAll(text).forEach { match ->
            val token = match.groupValues[2]
            parseAmountToken(token)?.let { amount ->
                candidates.add(
                    AmountCandidate(
                        amount = amount,
                        explicitSign = detectSign(token),
                        score = 40 + scoreContext(text, match.range)
                    )
                )
            }
        }
        keywordAfterAmount.findAll(text).forEach { match ->
            val token = match.groupValues[1]
            parseAmountToken(token)?.let { amount ->
                candidates.add(
                    AmountCandidate(
                        amount = amount,
                        explicitSign = detectSign(token),
                        score = 40 + scoreContext(text, match.range)
                    )
                )
            }
        }

        if (candidates.isEmpty()) return null
        return candidates
            .filter { it.amount > 0L }
            .maxWithOrNull(compareBy<AmountCandidate> { it.score }.thenBy { it.amount })
            ?.takeIf { it.score > 0 }
    }

    private fun parseAmountToken(token: String): Long? {
        val numeric = token.replace(",", "").trim()
        val value = numeric.toLongOrNull()?.absoluteValue ?: return null
        return value.takeIf { it > 0L && it < 10_000_000_000L }
    }

    private fun detectSign(token: String): Int {
        return when {
            token.trim().startsWith("-") -> -1
            token.trim().startsWith("+") -> 1
            else -> 0
        }
    }

    private fun scoreContext(text: String, range: IntRange): Int {
        val lower = text.lowercase()
        val start = (range.first - 12).coerceAtLeast(0)
        val end = (range.last + 12).coerceAtMost(lower.lastIndex)
        if (start > end) return 0
        val context = lower.substring(start, end + 1)

        var score = 0
        score += amountBoostHints.count { context.contains(it) } * 5
        score -= amountPenaltyHints.count { context.contains(it) } * 6
        return score
    }

    private data class AmountCandidate(
        val amount: Long,
        val explicitSign: Int,
        val score: Int
    )
}
