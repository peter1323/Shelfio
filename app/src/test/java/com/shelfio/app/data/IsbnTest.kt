package com.shelfio.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnTest {

    @Test
    fun validIsbn13_withHyphensAndSpaces() {
        assertEquals("9780140328721", normalizeIsbn("978-0-14-032872-1"))
        assertEquals("9780140328721", normalizeIsbn(" 978 0140328721 "))
    }

    @Test
    fun isbn10_isConvertedTo13() {
        assertEquals("9780140328721", normalizeIsbn("0-14-032872-6"))
        assertEquals("9780306406157", normalizeIsbn("0-306-40615-2"))
    }

    @Test
    fun isbn10_withXCheckDigit() {
        assertEquals("9780804429573", normalizeIsbn("080442957X"))
    }

    @Test
    fun invalidChecksum_isRejected() {
        assertNull(normalizeIsbn("9780140328722"))
        assertNull(normalizeIsbn("0140328727"))
    }

    @Test
    fun nonBookBarcode_isRejected() {
        // EAN-13 válido pero no es de libro (no empieza por 978/979)
        assertNull(normalizeIsbn("4006381333931"))
    }

    @Test
    fun wrongLength_isRejected() {
        assertNull(normalizeIsbn("12345"))
        assertNull(normalizeIsbn(""))
    }
}
