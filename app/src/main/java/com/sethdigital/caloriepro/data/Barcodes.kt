package com.sethdigital.caloriepro.data

object Barcodes {

    /**
     * Puts a product barcode into one canonical form, so the same product is
     * matched however the scanner reported it: a 12-digit UPC-A becomes the
     * equivalent 13-digit EAN-13, and a 14-digit code with a leading zero
     * (GTIN-14 packaging) drops it. Returns null for anything that is not a
     * product barcode.
     */
    fun normalize(raw: String?): String? {
        val digits = raw?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        return when (digits.length) {
            8, 13 -> digits
            12 -> "0$digits"
            14 -> if (digits.startsWith('0')) digits.drop(1) else digits
            else -> null
        }
    }
}
