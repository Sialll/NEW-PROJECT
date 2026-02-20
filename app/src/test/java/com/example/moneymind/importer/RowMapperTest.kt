package com.example.moneymind.importer

import com.example.moneymind.domain.EntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RowMapperTest {
    @Test
    fun hyundaiCardStatementRow_mapsToExpense() {
        val row = mapOf(
            "이용일" to "2026년 02월 11일",
            "이용가맹점" to "(주)이니시스 - 애플코리아 유한회사",
            "이용금액" to "1,610,000",
            "결제원금" to "536,800",
            "할부/회차" to "3/1"
        )

        val parsed = RowMapper.mapRow(row, EntrySource.EXCEL_IMPORT)
        assertNotNull(parsed)
        assertEquals(-536_800L, parsed?.signedAmount)
        assertEquals("(주)이니시스 - 애플코리아 유한회사", parsed?.description)
    }

    @Test
    fun depositRow_mapsToIncome() {
        val row = mapOf(
            "거래일" to "2026-02-20",
            "적요" to "급여 입금",
            "입금액" to "2,500,000"
        )

        val parsed = RowMapper.mapRow(row, EntrySource.CSV_IMPORT)
        assertNotNull(parsed)
        assertEquals(2_500_000L, parsed?.signedAmount)
        assertEquals("급여 입금", parsed?.description)
    }

    @Test
    fun dateParser_supportsKoreanDateFormat() {
        val parsed = RowMapper.parseDate("2026년 02월 20일")
        assertNotNull(parsed)
        assertEquals(2026, parsed?.year)
        assertEquals(2, parsed?.monthValue)
        assertEquals(20, parsed?.dayOfMonth)
    }
}

