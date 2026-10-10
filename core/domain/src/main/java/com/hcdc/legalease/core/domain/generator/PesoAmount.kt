package com.hcdc.legalease.core.domain.generator

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Peso amounts the way Philippine contracts state them: in words, then in figures, e.g.
 * "FIFTEEN THOUSAND PESOS (PHP 15,000.00)" or "ONE HUNDRED PESOS AND 50/100 (PHP 100.50)".
 */
object PesoAmount {

    private val ONES = listOf(
        "", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN",
        "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN",
    )
    private val TENS = listOf("", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY")
    private val SCALES = listOf("", "THOUSAND", "MILLION", "BILLION", "TRILLION")
    private const val THOUSAND = 1000L
    private const val HUNDRED = 100
    private const val TWENTY = 20
    private const val TEN = 10

    fun format(amount: BigDecimal): String {
        val rounded = amount.setScale(2, RoundingMode.HALF_UP)
        val pesos = rounded.toBigInteger().toLong()
        val centavos = rounded.remainder(BigDecimal.ONE).movePointRight(2).toInt()
        val words = buildString {
            append(if (pesos == 0L) "ZERO" else words(pesos))
            append(if (pesos == 1L) " PESO" else " PESOS")
            if (centavos > 0) append(" AND ").append("%02d".format(centavos)).append("/100")
        }
        return "$words (PHP ${figures(rounded)})"
    }

    fun figures(amount: BigDecimal): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(amount.setScale(2, RoundingMode.HALF_UP))

    /** 1 234 567 -> "ONE MILLION TWO HUNDRED THIRTY-FOUR THOUSAND FIVE HUNDRED SIXTY-SEVEN". */
    fun words(number: Long): String {
        require(number in 1 until THOUSAND * THOUSAND * THOUSAND * THOUSAND * THOUSAND) { "Out of range: $number" }
        val groups = mutableListOf<String>()
        var rest = number
        var scale = 0
        while (rest > 0) {
            val group = (rest % THOUSAND).toInt()
            if (group > 0) {
                groups.add(0, listOf(underThousand(group), SCALES[scale]).filter { it.isNotEmpty() }.joinToString(" "))
            }
            rest /= THOUSAND
            scale++
        }
        return groups.joinToString(" ")
    }

    private fun underThousand(n: Int): String {
        val parts = mutableListOf<String>()
        if (n >= HUNDRED) parts += "${ONES[n / HUNDRED]} HUNDRED"
        val rest = n % HUNDRED
        when {
            rest == 0 -> Unit
            rest < TWENTY -> parts += ONES[rest]
            rest % TEN == 0 -> parts += TENS[rest / TEN]
            else -> parts += "${TENS[rest / TEN]}-${ONES[rest % TEN]}"
        }
        return parts.joinToString(" ")
    }
}
