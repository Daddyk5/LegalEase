package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.model.Party
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How the templates write dates, counts, rates, and parties. */
internal object TemplateFormat {

    private val DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private const val SHORTEST_MONTH = 28
    private const val HUNDRED = 100
    private const val TEN = 10
    private const val FIRST = 1
    private const val SECOND = 2
    private const val THIRD = 3
    private val TEENS = 11..13

    /** "November 1, 2026" */
    fun date(date: LocalDate): String = date.format(DATE)

    /** "15th day of October 2026" */
    fun dayOf(date: LocalDate): String = "${ordinal(date.dayOfMonth)} day of ${date.format(MONTH_YEAR)}"

    /** "on or before the 5th day", with a note for days some months do not have. */
    fun dueDay(start: LocalDate): String {
        val day = start.dayOfMonth
        val base = "on or before the ${ordinal(day)} day"
        return if (day > SHORTEST_MONTH) "$base (or the last day, in a shorter month)" else base
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % HUNDRED in TEENS) {
            "th"
        } else {
            when (n % TEN) {
                FIRST -> "st"
                SECOND -> "nd"
                THIRD -> "rd"
                else -> "th"
            }
        }
        return "$n$suffix"
    }

    /** "twelve (12)" */
    fun count(n: Int) = "${PesoAmount.words(n.toLong()).lowercase()} ($n)"

    /** "five percent (5%)" for whole numbers, "2.5%" otherwise. */
    fun percent(rate: BigDecimal): String {
        val plain = rate.stripTrailingZeros().toPlainString()
        val whole = rate.stripTrailingZeros().scale() <= 0
        return if (whole) "${PesoAmount.words(rate.toLong()).lowercase()} percent ($plain%)" else "$plain%"
    }

    /** "JUAN DELA CRUZ, of legal age, married, Filipino, and residing at ..." */
    fun describe(p: Party): String =
        "${p.name.trim().uppercase()}, of legal age, ${p.civilStatus!!.label}, ${p.citizenship.trim()}, " +
            "and residing at ${p.address.trim()}"

    /** "Lessor" from "LESSOR". */
    fun titleCase(role: String) = role.lowercase().replaceFirstChar { it.titlecase() }
}
