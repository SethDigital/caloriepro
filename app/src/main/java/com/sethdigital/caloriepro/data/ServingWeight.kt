package com.sethdigital.caloriepro.data

/** How much one serving weighs, read from a serving size such as "2/3 cup (55g)". */
data class ServingWeight(val amount: Double, val unit: String) {

    /** Servings in [weight] of this food, e.g. 110 g of a 55 g serving is 2. */
    fun servingsIn(weight: Double): Double = weight / amount

    /** The weight of [servings] servings. */
    fun weightOf(servings: Double): Double = servings * amount

    companion object {
        private val METRIC = Regex("""(\d+(?:[.,]\d+)?)\s*(g|gr|grams?|ml|mls|millilit(?:er|re)s?)\b""", RegexOption.IGNORE_CASE)

        /**
         * The first metric weight or volume in [servingSize] ("30 g", "1 can
         * (355 mL)"), or null when it has none ("1 cup"). Volumes are kept in
         * ml; they are not converted to grams.
         */
        fun parse(servingSize: String?): ServingWeight? {
            if (servingSize.isNullOrBlank()) return null
            val m = METRIC.find(servingSize) ?: return null
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
            val unit = if (m.groupValues[2].lowercase().startsWith("m")) "ml" else "g"
            return ServingWeight(amount, unit)
        }
    }
}
