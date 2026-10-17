package com.sethdigital.caloriepro.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Whole number with grouping, e.g. 1,234. */
fun Double.formatWhole(): String = NumberFormat.getIntegerInstance().format(roundToLong())

fun Int.formatWhole(): String = NumberFormat.getIntegerInstance().format(this)

/** Up to one decimal place, dropping a trailing ".0". */
fun Double.formatAmount(): String {
    val rounded = Math.round(this * 10) / 10.0
    return if (rounded % 1.0 == 0.0) {
        NumberFormat.getIntegerInstance().format(rounded.roundToLong())
    } else {
        String.format(Locale.getDefault(), "%.1f", rounded)
    }
}

/** For pre-filling text fields: plain digits, no grouping, "." decimal. */
fun Double.toInputText(): String {
    val rounded = Math.round(this * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.roundToLong().toString() else rounded.toString()
}

/** Parses user input, accepting either "," or "." as the decimal separator. */
fun String.parseAmount(): Double? = trim().replace(',', '.').toDoubleOrNull()

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dayWithYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

fun LocalDate.friendlyName(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> format(if (year == today.year) dayFormat else dayWithYearFormat)
}

fun LocalDate.longName(): String = format(dayWithYearFormat)
