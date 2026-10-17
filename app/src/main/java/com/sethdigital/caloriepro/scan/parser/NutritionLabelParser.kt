package com.sethdigital.caloriepro.scan.parser

import java.text.Normalizer
import java.util.Locale

/**
 * Pulls calories, macros and caffeine out of the text of a nutrition label.
 *
 * Handles US/Canadian "Nutrition Facts" panels and UK/EU tables (energy in
 * kJ/kcal, "of which sugars", salt instead of sodium, per-100g columns), and
 * tolerates the usual OCR mistakes such as "Og" for "0g".
 */
object NutritionLabelParser {

    fun parse(lines: List<OcrLine>): NutritionFacts {
        val byRow = parseRows(RowGrouper.group(lines))
        // Row grouping can occasionally merge two label lines into one; the
        // raw lines are a fallback for anything the grouped rows missed.
        val byLine = parseRows(lines.map { it.text })
        return byRow.orElse(byLine)
    }

    fun parseRows(rawRows: List<String>): NutritionFacts {
        val rows = rawRows.map(::normalize).filter { it.isNotEmpty() }
        if (rows.isEmpty()) return NutritionFacts()

        val layout = detectLayout(rows)
        val column = layout.column
        val servingSize = findServingSize(rows) ?: layout.servingFallback

        return NutritionFacts(
            calories = (findValue(rows, CALORIES, column) ?: findValue(rows, KILOJOULES, column))?.roundTo(0),
            proteinG = findValue(rows, PROTEIN, column)?.roundTo(1),
            carbsG = findValue(rows, CARBS, column)?.roundTo(1),
            fatG = findValue(rows, TOTAL_FAT, column)?.roundTo(1),
            saturatedFatG = findValue(rows, SATURATED_FAT, column)?.roundTo(1),
            sugarG = findValue(rows, SUGARS, column)?.roundTo(1),
            fiberG = findValue(rows, FIBER, column)?.roundTo(1),
            sodiumMg = (findValue(rows, SODIUM, column) ?: findValue(rows, SALT, column))?.roundTo(0),
            caffeineMg = findCaffeine(rows, servingSize)?.roundTo(0),
            servingSize = servingSize,
            servingsPerContainer = findServingsPerContainer(rows),
        )
    }

    // ---------------------------------------------------------------- nutrients

    private enum class Unit { KCAL, KJ, GRAM, MILLIGRAM }

    private class Spec(
        val label: Regex,
        val unit: Unit,
        /** Largest believable value, in [unit]; anything above is a misread. */
        val max: Double,
        val exclude: Regex? = null,
        /** Multiplier applied to the result (used to turn salt into sodium). */
        val factor: Double = 1.0,
    )

    private val CALORIES = Spec(
        label = Regex("""\bca[l1i]or[il1]?e?s?|\benergy\b|\bkcal\b"""),
        unit = Unit.KCAL,
        max = 5000.0,
        exclude = Regex("""from\s*fat|fat\s*cal|calories\s*from|\b2000\b.*\b2500\b"""),
    )

    private val KILOJOULES = Spec(
        label = Regex("""\benergy\b|\bkj\b"""),
        unit = Unit.KJ,
        max = 5000.0,
    )

    private val TOTAL_FAT = Spec(
        label = Regex("""\btotal\s*fat|^fat\b|\bfat\s*(?=[<\d.])"""),
        unit = Unit.GRAM,
        max = 500.0,
        exclude = Regex("""saturat|trans\b|poly|mono|calories\s*from|from\s*fat|fat\s*cal"""),
    )

    private val SATURATED_FAT = Spec(
        label = Regex("""\bsat(?:urated|\.)?\s*fat|\bsaturates\b|\bsaturated\b"""),
        unit = Unit.GRAM,
        max = 300.0,
    )

    private val CARBS = Spec(
        label = Regex("""\btotal\s*carb|\bcarbohydrates?\b|\bcarbs?\b"""),
        unit = Unit.GRAM,
        max = 1000.0,
    )

    private val FIBER = Spec(
        label = Regex("""\b(?:dietary\s*)?fib(?:er|re)s?\b|\bfibra\b"""),
        unit = Unit.GRAM,
        max = 300.0,
    )

    private val SUGARS = Spec(
        label = Regex("""\b(?:total\s*)?sugars?\b|\bazucares\b"""),
        unit = Unit.GRAM,
        max = 1000.0,
        exclude = Regex("""added|alcohol|includes|no\s*sugar|sugar\s*free"""),
    )

    private val PROTEIN = Spec(
        label = Regex("""\bprote[il1]n"""),
        unit = Unit.GRAM,
        max = 1000.0,
    )

    private val SODIUM = Spec(
        label = Regex("""\bsod[il1]um\b|\bsodio\b"""),
        unit = Unit.MILLIGRAM,
        max = 50000.0,
    )

    /** EU labels list salt in grams; sodium is salt / 2.5. */
    private val SALT = Spec(
        label = Regex("""\bsalt\b"""),
        unit = Unit.GRAM,
        max = 100.0,
        factor = 400.0,
    )

    /** Footnote and reference rows that mention nutrients but are not values. */
    private val SKIP_ROW = Regex(
        """per\s*gram|daily\s*diet|calorie\s*diet|a\s*day\b|nutrition\s*advice|your\s*daily"""
    )

    private data class Token(val value: Double, val unit: String?)

    private val NUMBER = Regex("""(?<![\d.])(\d*\.\d+|\d+)\s*(kcal|kj|mcg|µg|ug|mg|ml|g|cal)?\s*(%)?""")
    private val LEADING_NUMBER = Regex("""^[<~]?\s*(\d*\.\d+|\d+)""")

    /**
     * Numbers in [row] from [start] on. Percentages are % Daily Value, not
     * amounts, so they are dropped unless [keepPercent] (calories never have a
     * percentage, but "Calories 110 % Daily Value" puts a % right after one).
     */
    private fun tokensAfter(row: String, start: Int, keepPercent: Boolean = false): List<Token> =
        NUMBER.findAll(row, start.coerceAtMost(row.length))
            .filter { keepPercent || it.groups[3] == null }
            .mapNotNull { m -> m.groupValues[1].toDoubleOrNull()?.let { Token(it, m.groups[2]?.value) } }
            .toList()

    private fun convert(token: Token, unit: Unit): Double? = when (unit) {
        Unit.KCAL -> when (token.unit) {
            null, "kcal", "cal" -> token.value
            else -> null
        }
        Unit.KJ -> if (token.unit == "kj") token.value / 4.184 else null
        Unit.GRAM -> when (token.unit) {
            null, "g" -> token.value
            "mg" -> token.value / 1000.0
            "mcg", "µg", "ug" -> token.value / 1_000_000.0
            else -> null
        }
        Unit.MILLIGRAM -> when (token.unit) {
            null, "mg" -> token.value
            "g" -> token.value * 1000.0
            "mcg", "µg", "ug" -> token.value / 1000.0
            else -> null
        }
    }

    private fun findValue(rows: List<String>, spec: Spec, column: Int): Double? {
        val keepPercent = spec.unit == Unit.KCAL
        for ((i, row) in rows.withIndex()) {
            if (SKIP_ROW.containsMatchIn(row)) continue
            // Only look at the part of the row before anything excluded, so
            // "Calories 250 Calories from Fat 110" still yields 250.
            val text = spec.exclude?.find(row)?.let { row.substring(0, it.range.first) } ?: row
            val label = spec.label.find(text) ?: continue

            var values = tokensAfter(text, label.range.last + 1, keepPercent).mapNotNull { convert(it, spec.unit) }
            if (values.isEmpty()) {
                // The amount sometimes lands on the row below the label.
                val next = rows.getOrNull(i + 1)
                if (next != null && LEADING_NUMBER.containsMatchIn(next) && !SKIP_ROW.containsMatchIn(next)) {
                    values = tokensAfter(next, 0, keepPercent).mapNotNull { convert(it, spec.unit) }
                }
            }
            val value = values.getOrNull(column) ?: values.firstOrNull() ?: continue
            if (value in 0.0..spec.max) return value * spec.factor
        }
        return null
    }

    // ----------------------------------------------------------------- caffeine

    private const val CAFFEINE_WORD = """(?:caf+e[il1]ne?|caf+eina|kof+ein)"""
    private const val AMOUNT_MG = """(\d*\.\d+|\d+)\s*mg(\s*/\s*100\s*ml)?"""
    /** Text between "caffeine" and its amount may not run into another nutrient or list item. */
    private const val CAFFEINE_GAP =
        """(?:(?!sodium|salt|sugar|protein|fat|carb|fib|calor|vitamin|potassium|calcium|iron|taurine)[^\d,;.]){0,30}?"""
    private val CAFFEINE_THEN_AMOUNT = Regex("""$CAFFEINE_WORD$CAFFEINE_GAP$AMOUNT_MG""")
    private val AMOUNT_THEN_CAFFEINE = Regex("""$AMOUNT_MG\s*(?:of\s*)?$CAFFEINE_WORD""")
    private val ML_IN_SERVING = Regex("""(\d*\.\d+|\d+)\s*ml\b""")

    /** Caffeine is often outside the main panel ("Contains 80mg caffeine"), so search all text. */
    private fun findCaffeine(rows: List<String>, servingSize: String?): Double? {
        val text = rows.joinToString(" ")
        val match = CAFFEINE_THEN_AMOUNT.find(text) ?: AMOUNT_THEN_CAFFEINE.find(text) ?: return null
        var mg = match.groupValues[1].toDoubleOrNull() ?: return null
        val per100ml = match.groupValues[2].isNotEmpty()
        if (per100ml) {
            val servingMl = servingSize?.let { ML_IN_SERVING.find(it) }?.groupValues?.get(1)?.toDoubleOrNull()
            if (servingMl != null) mg = mg * servingMl / 100.0
        }
        return mg.takeIf { it in 0.0..2000.0 }
    }

    // ------------------------------------------------------------ serving info

    private val SERVING_SIZE = Regex("""\bserv(?:ing|\.)?\s*size\s*:?\s*(.*)""")
    private val SERVING_TRAILER = Regex("""\s*(amount\s*per\s*serving|calories|%\s*daily).*$""")
    private val PER_SERVING_AMOUNT = Regex(
        """\bper\s*(?:serving|portion)\s*\(?\s*(\d*\.\d+|\d+)\s*(g|ml)\b|\bper\s*(\d*\.\d+|\d+)\s*(g|ml)\s*(?:serving|portion)"""
    )
    private const val CONTAINER = """(?:container|package|pack|bottle|can|bag|box|jar|tub)"""
    private val SERVINGS_BEFORE = Regex("""(\d*\.\d+|\d+)\s*servings?\s*per\s*$CONTAINER""")
    private val SERVINGS_AFTER =
        Regex("""servings?\s*per\s*$CONTAINER\s*:?\s*(?:about\s*|approx\.?\s*|approximately\s*)?(\d*\.\d+|\d+)""")

    private fun findServingSize(rows: List<String>): String? {
        for ((i, row) in rows.withIndex()) {
            val m = SERVING_SIZE.find(row) ?: continue
            var size = m.groupValues[1].replace(SERVING_TRAILER, "").trim()
            if (size.isEmpty()) {
                val next = rows.getOrNull(i + 1)
                if (next != null && next.length <= 30 && next.any { it.isDigit() }) size = next
            }
            if (size.isNotEmpty()) return size.take(40)
        }
        for (row in rows) {
            val m = PER_SERVING_AMOUNT.find(row) ?: continue
            val (amount, unit) = if (m.groupValues[1].isNotEmpty()) {
                m.groupValues[1] to m.groupValues[2]
            } else {
                m.groupValues[3] to m.groupValues[4]
            }
            return "${amount.trimNumber()} $unit"
        }
        return null
    }

    private fun findServingsPerContainer(rows: List<String>): Double? {
        for (row in rows) {
            val m = SERVINGS_AFTER.find(row) ?: SERVINGS_BEFORE.find(row) ?: continue
            val n = m.groupValues[1].toDoubleOrNull() ?: continue
            if (n > 0 && n <= 1000) return n
        }
        return null
    }

    // ------------------------------------------------------------------ layout

    private data class Layout(val column: Int, val servingFallback: String?)

    private val PER_100 = Regex("""(?:per|/)\s*100\s*(g|ml)\b""")
    private val PER_SERVING_HEADER =
        Regex("""\bper\s*(?:serving|portion)|\bserving\b|\bportion\b|\bper\s*(?!100\s*(?:g|ml)\b)\d+(?:\.\d+)?\s*(?:g|ml)\b""")

    /**
     * EU tables often have a "per 100g" column next to a "per serving" one.
     * Work out which column holds the per-serving amounts.
     */
    private fun detectLayout(rows: List<String>): Layout {
        for (row in rows) {
            val per100 = PER_100.find(row) ?: continue
            val perServing = PER_SERVING_HEADER.find(row)
            if (perServing != null) {
                return Layout(column = if (per100.range.first < perServing.range.first) 1 else 0, servingFallback = null)
            }
            if (rows.none { PER_SERVING_HEADER.containsMatchIn(it) }) {
                // Only per-100 values on this label.
                return Layout(column = 0, servingFallback = "100 ${per100.groupValues[1]}")
            }
            return Layout(column = 0, servingFallback = null)
        }
        return Layout(column = 0, servingFallback = null)
    }

    // ----------------------------------------------------------- normalizing

    private val DIACRITICS = Regex("""\p{Mn}+""")
    private val THOUSANDS = Regex("""(?<=\d),(?=\d{3}(?!\d))""")
    private val DECIMAL_COMMA = Regex("""(?<=\d),(?=\d)""")
    private val O_IN_NUMBER = Regex("""(?<=\d)o(?=[o\d]|m?g\b|ml\b|kcal|kj|%|\s|$)|(?<![a-z])o(?=\.?\d)""")
    private val O_BEFORE_UNIT = Regex("""(?<![a-z\d])o(?=\s?m?g\b)""")
    private val L_AS_ONE = Regex("""(?<=\d)[il](?=\d|\s?m?g\b)|(?<![a-z])[il](?=\d)""")
    private val RN_AS_M = Regex("""(?<=\d\s?)rng\b""")
    private val WHITESPACE = Regex("""\s+""")

    internal fun normalize(raw: String): String {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFD).replace(DIACRITICS, "")
        s = s.lowercase(Locale.ROOT).replace('|', ' ')
        // Twice, so runs like "1oo" become "100".
        repeat(2) { s = s.replace(O_IN_NUMBER, "0") }
        s = s.replace(O_BEFORE_UNIT, "0")
        s = s.replace(L_AS_ONE, "1")
        s = s.replace(RN_AS_M, "mg")
        s = s.replace(THOUSANDS, "").replace(DECIMAL_COMMA, ".")
        return s.replace(WHITESPACE, " ").trim()
    }

    private fun NutritionFacts.orElse(other: NutritionFacts) = NutritionFacts(
        calories = calories ?: other.calories,
        proteinG = proteinG ?: other.proteinG,
        carbsG = carbsG ?: other.carbsG,
        fatG = fatG ?: other.fatG,
        saturatedFatG = saturatedFatG ?: other.saturatedFatG,
        sugarG = sugarG ?: other.sugarG,
        fiberG = fiberG ?: other.fiberG,
        sodiumMg = sodiumMg ?: other.sodiumMg,
        caffeineMg = caffeineMg ?: other.caffeineMg,
        servingSize = servingSize ?: other.servingSize,
        servingsPerContainer = servingsPerContainer ?: other.servingsPerContainer,
    )

    private fun Double.roundTo(decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return Math.round(this * factor) / factor
    }

    private fun String.trimNumber(): String = toDoubleOrNull()?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
    } ?: this
}
