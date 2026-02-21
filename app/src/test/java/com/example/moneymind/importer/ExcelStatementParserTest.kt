package com.example.moneymind.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExcelStatementParserTest {
    @Suppress("UNCHECKED_CAST")
    @Test
    fun alignValuesToHeaders_insertsBlankForMissingAmountColumn() {
        val parser = ExcelStatementParser()
        val method = ExcelStatementParser::class.java.getDeclaredMethod(
            "alignValuesToHeaders",
            List::class.java,
            List::class.java
        )
        method.isAccessible = true

        val headers = listOf(
            "이용일",
            "이용카드",
            "이용가맹점",
            "이용금액",
            "할부/회차",
            "예상적립/할인율(%)",
            "예상적립/할인",
            "결제원금",
            "결제후잔액",
            "수수료(이자)"
        )
        val values = listOf(
            "2026년 02월 17일",
            "본인L Hyundai Mobility카드",
            "(주)다날 - 카카오 20,000",
            "",
            "1.0%",
            "200",
            "20,000",
            "0",
            "0"
        )

        val aligned = method.invoke(parser, headers, values) as List<String>

        assertEquals(10, aligned.size)
        assertEquals("", aligned[3])
        assertEquals("20,000", aligned[7])
    }
}
