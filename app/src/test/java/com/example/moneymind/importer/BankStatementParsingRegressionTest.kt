package com.example.moneymind.importer

import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BankStatementParsingRegressionTest {
    @Test
    fun mapRow_supportsCurrencyUnitHeaders() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "거래일자" to "2026-02-21",
                "거래시간" to "07:10:12",
                "적요" to "타행인터넷뱅킹",
                "출금(원)" to "0.0",
                "입금(원)" to "175.0",
                "내용" to "KR-GOOGLE"
            ),
            source = EntrySource.EXCEL_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(175L, parsed!!.signedAmount)
        assertEquals("타행인터넷뱅킹", parsed.description)
    }

    @Test
    fun parseSignedAmount_handlesDecimalTextWithoutDigitInflation() {
        assertEquals(175L, RowMapper.parseSignedAmount("175.0"))
        assertEquals(-11992L, RowMapper.parseSignedAmount("-11,992.0"))
        assertEquals(300000L, RowMapper.parseSignedAmount("300000"))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun parseWorkbook_detectsKoreanHeaderAfterPreambleRows() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("신한")
        sheet.createRow(0).createCell(0).setCellValue("거래내역조회")
        sheet.createRow(2).createCell(0).setCellValue("계좌번호")

        val header = sheet.createRow(6)
        listOf(
            "거래일자",
            "거래시간",
            "적요",
            "출금(원)",
            "입금(원)",
            "내용",
            "잔액(원)",
            "거래점"
        ).forEachIndexed { index, value ->
            header.createCell(index).setCellValue(value)
        }

        sheet.createRow(7).apply {
            createCell(0).setCellValue("2026-02-21")
            createCell(1).setCellValue("07:10:12")
            createCell(2).setCellValue("타행인터넷뱅킹")
            createCell(3).setCellValue(0.0)
            createCell(4).setCellValue(175.0)
            createCell(5).setCellValue("KR-GOOGLE")
        }
        sheet.createRow(8).apply {
            createCell(0).setCellValue("2026-02-20")
            createCell(1).setCellValue("15:58:14")
            createCell(2).setCellValue("오픈뱅킹 이체")
            createCell(3).setCellValue(11992.0)
            createCell(4).setCellValue(0.0)
            createCell(5).setCellValue("토뱅 김도현")
        }

        val bytes = ByteArrayOutputStream().use { output ->
            workbook.use { wb -> wb.write(output) }
            output.toByteArray()
        }

        val parser = ExcelStatementParser()
        val method = ExcelStatementParser::class.java.getDeclaredMethod("parseWorkbook", ByteArray::class.java)
        method.isAccessible = true

        val parsed = method.invoke(parser, bytes) as List<ParsedRecord>
        assertEquals(2, parsed.size)
        assertEquals(175L, parsed[0].signedAmount)
        assertEquals(-11992L, parsed[1].signedAmount)
    }
}

