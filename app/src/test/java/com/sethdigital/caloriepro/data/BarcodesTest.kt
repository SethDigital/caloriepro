package com.sethdigital.caloriepro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodesTest {

    @Test
    fun `UPC-A and EAN-13 of the same product match`() {
        assertEquals("0049000028911", Barcodes.normalize("049000028911")) // UPC-A
        assertEquals("0049000028911", Barcodes.normalize("0049000028911")) // EAN-13
        assertEquals("0049000028911", Barcodes.normalize("00049000028911")) // GTIN-14
    }

    @Test
    fun `EAN-8 and EAN-13 are kept as they are`() {
        assertEquals("96385074", Barcodes.normalize("96385074"))
        assertEquals("5012345678900", Barcodes.normalize(" 5012345678900 "))
    }

    @Test
    fun `anything else is not a product barcode`() {
        assertNull(Barcodes.normalize(null))
        assertNull(Barcodes.normalize(""))
        assertNull(Barcodes.normalize("12345"))
        assertNull(Barcodes.normalize("https://example.com"))
        assertNull(Barcodes.normalize("50123456789A0"))
    }
}
