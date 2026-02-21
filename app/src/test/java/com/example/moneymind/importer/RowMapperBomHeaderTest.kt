package com.example.moneymind.importer

import com.example.moneymind.domain.EntrySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RowMapperBomHeaderTest {
    @Test
    fun mapRow_acceptsUtf8BomPrefixedDateHeader() {
        val row = mapOf(
            "\uFEFFdate" to "2026-02-21",
            "description" to "BOM import regression",
            "amount" to "-12,345"
        )

        val parsed = RowMapper.mapRow(row, EntrySource.CSV_IMPORT)

        assertNotNull(parsed)
        assertEquals(-12_345L, parsed?.signedAmount)
        assertEquals("BOM import regression", parsed?.description)
    }
}
