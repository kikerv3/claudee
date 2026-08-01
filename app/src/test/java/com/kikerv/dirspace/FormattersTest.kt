package com.kikerv.dirspace

import com.kikerv.dirspace.util.formatBytes
import com.kikerv.dirspace.util.formatPercent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    private val locale = Locale.US

    @Test
    fun `los bytes sueltos se muestran sin decimales`() {
        assertEquals("0 B", formatBytes(0, locale))
        assertEquals("512 B", formatBytes(512, locale))
    }

    @Test
    fun `usa unidades binarias`() {
        assertEquals("1.00 KB", formatBytes(1024, locale))
        assertEquals("1.00 MB", formatBytes(1024L * 1024, locale))
        assertEquals("1.50 GB", formatBytes(1536L * 1024 * 1024, locale))
    }

    @Test
    fun `los porcentajes minusculos no se redondean a cero`() {
        assertEquals("<0.1 %", formatPercent(0.0001f, locale))
        assertEquals("0.0 %", formatPercent(0f, locale))
        assertEquals("50 %", formatPercent(0.5f, locale))
    }
}
