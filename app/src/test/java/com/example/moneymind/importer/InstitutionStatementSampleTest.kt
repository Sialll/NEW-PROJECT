package com.example.moneymind.importer

import com.example.moneymind.domain.EntrySource
import com.example.moneymind.domain.ParsedRecord
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InstitutionStatementSampleTest {
    @Test
    fun kbBankTransferRow_mapsCounterpartyAndTime() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "거래일자" to "2026-03-02",
                "거래시간" to "08:15:44",
                "적요" to "인터넷이체",
                "출금액" to "58,000",
                "거래상대" to "홍길동",
                "계좌번호" to "123-45-678901"
            ),
            source = EntrySource.CSV_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(-58_000L, parsed!!.signedAmount)
        assertEquals("인터넷이체", parsed.description)
        assertEquals("홍길동", parsed.counterpartyName)
        assertEquals("123-45-678901", parsed.accountMask)
        assertEquals(LocalTime.of(8, 15, 44), parsed.occurredAt.toLocalTime())
    }

    @Test
    fun shinhanBankIncomeRow_mapsSummaryAndMerchantSeparately() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "거래일자" to "2026-03-03",
                "거래시간" to "21:14:03",
                "적요" to "타행이체입금",
                "입금(원)" to "1,250,000.0",
                "내용" to "ACME PAYROLL"
            ),
            source = EntrySource.EXCEL_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(1_250_000L, parsed!!.signedAmount)
        assertEquals("타행이체입금", parsed.description)
        assertEquals("ACME PAYROLL", parsed.merchant)
    }

    @Test
    fun tossBankTransferRow_mapsFromAndToAccounts() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "거래일시" to "2026-03-04 13:02:11",
                "적요" to "내 계좌 이체",
                "출금액" to "11,992",
                "보낸계좌" to "토스뱅크 1234",
                "받는계좌" to "국민은행 5678",
                "받는분" to "도현"
            ),
            source = EntrySource.CSV_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(-11_992L, parsed!!.signedAmount)
        assertEquals("토스뱅크 1234", parsed.fromAccountMask)
        assertEquals("국민은행 5678", parsed.toAccountMask)
        assertEquals("도현", parsed.counterpartyName)
    }

    @Test
    fun kakaoBankOpenBankingRow_mapsMerchantAndSignedWithdrawal() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "거래일시" to "2026.03.05 07:42:08",
                "적요" to "오픈뱅킹 출금",
                "출금액" to "35,000",
                "내용" to "카카오페이"
            ),
            source = EntrySource.EXCEL_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(-35_000L, parsed!!.signedAmount)
        assertEquals("오픈뱅킹 출금", parsed.description)
        assertEquals("카카오페이", parsed.merchant)
    }

    @Test
    fun hyundaiCardInstallmentRow_usesPrincipalAmount() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "이용일" to "2026년 03월 06일",
                "이용가맹점" to "(주)이니시스 - 애플코리아 유한회사",
                "이용금액" to "1,610,000",
                "결제원금" to "536,800",
                "할부/회차" to "3/1"
            ),
            source = EntrySource.EXCEL_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(-536_800L, parsed!!.signedAmount)
        assertEquals("(주)이니시스 - 애플코리아 유한회사", parsed.merchant)
    }

    @Test
    fun samsungCardRow_keepsApprovalMemoSeparateFromMerchant() {
        val parsed = RowMapper.mapRow(
            row = mapOf(
                "승인일시" to "2026-03-07 11:41:08",
                "적요" to "일시불",
                "가맹점" to "스타벅스 선릉역점",
                "승인금액" to "5,700"
            ),
            source = EntrySource.EXCEL_IMPORT
        )

        assertNotNull(parsed)
        assertEquals(-5_700L, parsed!!.signedAmount)
        assertEquals("일시불", parsed.description)
        assertEquals("스타벅스 선릉역점", parsed.merchant)
    }

    @Test
    fun excelTextFallback_parsesTabbedBankExportAfterPreamble() {
        val parser = ExcelStatementParser()
        val text = """
            거래내역조회
            다운로드 일시	2026-03-08 09:00:00
            거래일자	거래시간	적요	출금(원)	입금(원)	내용
            2026-03-08	09:11:12	타행이체	0	175.0	네이버페이
        """.trimIndent()

        val parsed = invokeParseTextSpreadsheet(parser, text.encodeToByteArray())

        assertEquals(1, parsed.size)
        assertEquals(175L, parsed[0].signedAmount)
        assertEquals("타행이체", parsed[0].description)
        assertEquals("네이버페이", parsed[0].merchant)
    }

    @Test
    fun excelHtmlFallback_parsesCardTableExport() {
        val parser = ExcelStatementParser()
        val html = """
            <html>
            <body>
            <table>
              <tr><th>이용일</th><th>적요</th><th>가맹점</th><th>이용금액</th></tr>
              <tr><td>2026-03-09</td><td>일시불</td><td>쿠팡</td><td>18,900</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val parsed = invokeParseTextSpreadsheet(parser, html.toByteArray(StandardCharsets.UTF_8))

        assertEquals(1, parsed.size)
        assertEquals(-18_900L, parsed[0].signedAmount)
        assertEquals("일시불", parsed[0].description)
        assertEquals("쿠팡", parsed[0].merchant)
    }

    @Test
    fun workbookParser_handlesNonghyupStyleHeaderBlock() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("NH")
        sheet.createRow(0).createCell(0).setCellValue("농협 거래내역")
        sheet.createRow(1).createCell(0).setCellValue("조회기간")

        val header = sheet.createRow(4)
        listOf(
            "거래일자",
            "거래시간",
            "적요",
            "출금액",
            "입금액",
            "내용",
            "거래상대"
        ).forEachIndexed { index, value ->
            header.createCell(index).setCellValue(value)
        }

        sheet.createRow(5).apply {
            createCell(0).setCellValue("2026-03-10")
            createCell(1).setCellValue("14:05:09")
            createCell(2).setCellValue("오픈뱅킹 출금")
            createCell(3).setCellValue(42_500.0)
            createCell(4).setCellValue(0.0)
            createCell(5).setCellValue("토스페이")
            createCell(6).setCellValue("홍길동")
        }

        val bytes = ByteArrayOutputStream().use { output ->
            workbook.use { wb -> wb.write(output) }
            output.toByteArray()
        }

        val parsed = invokeParseWorkbook(ExcelStatementParser(), bytes)

        assertEquals(1, parsed.size)
        assertEquals(-42_500L, parsed[0].signedAmount)
        assertEquals("토스페이", parsed[0].merchant)
        assertEquals("홍길동", parsed[0].counterpartyName)
        assertEquals(LocalTime.of(14, 5, 9), parsed[0].occurredAt.toLocalTime())
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeParseTextSpreadsheet(parser: ExcelStatementParser, bytes: ByteArray): List<ParsedRecord> {
        val method = ExcelStatementParser::class.java.getDeclaredMethod("parseTextSpreadsheet", ByteArray::class.java)
        method.isAccessible = true
        return method.invoke(parser, bytes) as List<ParsedRecord>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeParseWorkbook(parser: ExcelStatementParser, bytes: ByteArray): List<ParsedRecord> {
        val method = ExcelStatementParser::class.java.getDeclaredMethod("parseWorkbook", ByteArray::class.java)
        method.isAccessible = true
        return method.invoke(parser, bytes) as List<ParsedRecord>
    }
}
