package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** A form field. Party fields are prefixed by the party, e.g. [FIRST_NAME] and [SECOND_NAME]. */
enum class FormField {
    FIRST_NAME, FIRST_CIVIL_STATUS, FIRST_CITIZENSHIP, FIRST_ADDRESS, FIRST_ID_TYPE,
    SECOND_NAME, SECOND_CIVIL_STATUS, SECOND_CITIZENSHIP, SECOND_ADDRESS, SECOND_ID_TYPE, PARTIES_OF_LEGAL_AGE,
    PROPERTY_DESCRIPTION, AMOUNT, DEPOSIT_MONTHS, INTEREST_RATE, INSTALLMENTS,
    START_DATE, END_DATE, PLACE_OF_EXECUTION, EXECUTION_DATE, WITNESS_ONE, WITNESS_TWO,
}

enum class FieldError {
    REQUIRED,
    INVALID_AMOUNT,
    AMOUNT_TOO_LARGE,
    INVALID_NUMBER,
    INVALID_DATE,
    END_NOT_AFTER_START,
    LEASE_OVER_99_YEARS,
    EXECUTION_AFTER_START,
    SAME_AS_OTHER_PARTY,
    WITNESS_IS_PARTY,
    MUST_CONFIRM,
}

/** The form's steps. Validation runs per step so errors appear where the user is. */
enum class FormStep { FIRST_PARTY, SECOND_PARTY, SUBJECT, TERMS, SIGNING }

/** The parsed values of a valid draft, ready for a template. */
data class ValidatedTerms(
    val amount: BigDecimal,
    val depositMonths: Int?,
    val interestRatePercent: BigDecimal?,
    val installments: Int?,
    /** Lease start or loan release; null for a sale. */
    val start: LocalDate?,
    /** Lease end or loan due date; null for a sale. */
    val end: LocalDate?,
    val executionDate: LocalDate,
)

object ContractValidator {

    /** Art. 1643: no lease for more than ninety-nine years shall be valid. */
    const val MAX_LEASE_YEARS = 99L

    fun steps(type: ContractType): List<FormStep> =
        FormStep.entries.filter { it != FormStep.SUBJECT || type != ContractType.LOAN }

    fun validate(draft: ContractDraft, step: FormStep): Map<FormField, FieldError> = when (step) {
        FormStep.FIRST_PARTY -> FieldChecks.party(draft.firstParty, first = true)
        FormStep.SECOND_PARTY -> buildMap {
            putAll(FieldChecks.party(draft.secondParty, first = false))
            if (FormField.SECOND_NAME !in this && sameName(draft.firstParty.name, draft.secondParty.name)) {
                put(FormField.SECOND_NAME, FieldError.SAME_AS_OTHER_PARTY)
            }
            // A contract with a minor is voidable (Art. 1390), so this is never assumed.
            if (!draft.partiesOfLegalAge) put(FormField.PARTIES_OF_LEGAL_AGE, FieldError.MUST_CONFIRM)
        }
        FormStep.SUBJECT ->
            if (draft.type != ContractType.LOAN && draft.propertyDescription.isBlank()) {
                mapOf(FormField.PROPERTY_DESCRIPTION to FieldError.REQUIRED)
            } else {
                emptyMap()
            }
        FormStep.TERMS -> FieldChecks.money(draft) + FieldChecks.term(draft)
        FormStep.SIGNING -> FieldChecks.signing(draft)
    }

    /** Every step at once; empty means the draft can be rendered. */
    fun validateAll(draft: ContractDraft): Map<FormField, FieldError> =
        steps(draft.type).fold(emptyMap()) { acc, step -> acc + validate(draft, step) }

    /** Parsed values, or null if the draft is not valid. */
    fun parse(draft: ContractDraft): ValidatedTerms? {
        if (validateAll(draft).isNotEmpty()) return null
        val installments = draft.type == ContractType.LOAN &&
            draft.paymentSchedule == PaymentSchedule.MONTHLY_INSTALLMENTS
        return ValidatedTerms(
            amount = parseAmount(draft.amount)!!,
            depositMonths = draft.depositMonths.trim().takeIf { it.isNotEmpty() }?.toInt(),
            interestRatePercent = draft.interestRate.trim().takeIf { it.isNotEmpty() }?.let(::parseAmount),
            installments = if (installments) draft.installments.trim().toInt() else null,
            start = parseDate(draft.startDate).takeIf { draft.type != ContractType.SALE },
            end = parseDate(draft.endDate).takeIf { draft.type != ContractType.SALE },
            executionDate = parseDate(draft.executionDate)!!,
        )
    }

    /** Accepts "15000", "15,000", "15,000.50". At most two decimal places. */
    fun parseAmount(text: String): BigDecimal? {
        val cleaned = text.trim().replace(",", "")
        if (!AMOUNT.matches(cleaned)) return null
        return cleaned.toBigDecimalOrNull()
    }

    fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    internal fun sameName(a: String, b: String) =
        a.isNotBlank() && normalize(a).equals(normalize(b), ignoreCase = true)

    private fun normalize(name: String) = name.trim().replace(WHITESPACE, " ")

    private val AMOUNT = Regex("""\d{1,15}(\.\d{1,2})?""")
    private val WHITESPACE = Regex("""\s+""")
}

/** Field-level checks behind [ContractValidator]. */
private object FieldChecks {

    /** Amounts above one trillion pesos are treated as typing mistakes. */
    private val MAX_AMOUNT = BigDecimal("1000000000000")
    private val MAX_RATE = BigDecimal("100")
    private const val MAX_DEPOSIT_MONTHS = 12
    private const val MAX_INSTALLMENTS = 600

    fun party(p: Party, first: Boolean): Map<FormField, FieldError> = buildMap {
        val required = listOf(
            p.name.isBlank() to (FormField.FIRST_NAME to FormField.SECOND_NAME),
            (p.civilStatus == null) to (FormField.FIRST_CIVIL_STATUS to FormField.SECOND_CIVIL_STATUS),
            p.citizenship.isBlank() to (FormField.FIRST_CITIZENSHIP to FormField.SECOND_CITIZENSHIP),
            p.address.isBlank() to (FormField.FIRST_ADDRESS to FormField.SECOND_ADDRESS),
            p.idType.isBlank() to (FormField.FIRST_ID_TYPE to FormField.SECOND_ID_TYPE),
        )
        required.forEach { (missing, fields) ->
            if (missing) put(if (first) fields.first else fields.second, FieldError.REQUIRED)
        }
    }

    fun money(draft: ContractDraft): Map<FormField, FieldError> = buildMap {
        amountError(draft.amount)?.let { put(FormField.AMOUNT, it) }
        if (draft.type == ContractType.LEASE || draft.type == ContractType.LEASE_OF_PERSONAL_PROPERTY) {
            intError(draft.depositMonths, required = false, max = MAX_DEPOSIT_MONTHS)
                ?.let { put(FormField.DEPOSIT_MONTHS, it) }
        }
        if (draft.type == ContractType.LOAN) {
            // Art. 1956: interest is due only if expressly stipulated in writing, so the rate is always
            // asked for; 0 states in writing that the loan bears no interest.
            rateError(draft.interestRate)?.let { put(FormField.INTEREST_RATE, it) }
            if (draft.paymentSchedule == PaymentSchedule.MONTHLY_INSTALLMENTS) {
                intError(draft.installments, required = true, max = MAX_INSTALLMENTS)
                    ?.let { put(FormField.INSTALLMENTS, it) }
            }
        }
    }

    /** Lease and loan dates; a sale has none. */
    fun term(draft: ContractDraft): Map<FormField, FieldError> = buildMap {
        if (draft.type == ContractType.SALE) return@buildMap
        val start = ContractValidator.parseDate(draft.startDate)
        val end = ContractValidator.parseDate(draft.endDate)
        if (start == null) put(FormField.START_DATE, dateError(draft.startDate))
        if (end == null) put(FormField.END_DATE, dateError(draft.endDate))
        if (start == null || end == null) return@buildMap
        val tooLong = draft.type != ContractType.LOAN &&
            end.isAfter(start.plusYears(ContractValidator.MAX_LEASE_YEARS))
        when {
            !end.isAfter(start) -> put(FormField.END_DATE, FieldError.END_NOT_AFTER_START)
            tooLong -> put(FormField.END_DATE, FieldError.LEASE_OVER_99_YEARS)
        }
    }

    fun signing(draft: ContractDraft): Map<FormField, FieldError> = buildMap {
        if (draft.placeOfExecution.isBlank()) put(FormField.PLACE_OF_EXECUTION, FieldError.REQUIRED)
        val executed = ContractValidator.parseDate(draft.executionDate)
        // The contract must be signed on or before the lease starts or the loan is released.
        val start = ContractValidator.parseDate(draft.startDate).takeIf { draft.type != ContractType.SALE }
        when {
            executed == null -> put(FormField.EXECUTION_DATE, dateError(draft.executionDate))
            start != null && executed.isAfter(start) -> put(FormField.EXECUTION_DATE, FieldError.EXECUTION_AFTER_START)
        }
        val parties = listOf(draft.firstParty.name, draft.secondParty.name)
        witness(draft.witnessOne, parties)?.let { put(FormField.WITNESS_ONE, it) }
        witness(draft.witnessTwo, parties)?.let { put(FormField.WITNESS_TWO, it) }
    }

    private fun witness(name: String, parties: List<String>): FieldError? = when {
        name.isBlank() -> FieldError.REQUIRED
        parties.any { ContractValidator.sameName(it, name) } -> FieldError.WITNESS_IS_PARTY
        else -> null
    }

    private fun amountError(text: String): FieldError? {
        if (text.isBlank()) return FieldError.REQUIRED
        val amount = ContractValidator.parseAmount(text) ?: return FieldError.INVALID_AMOUNT
        return when {
            amount.signum() <= 0 -> FieldError.INVALID_AMOUNT
            amount > MAX_AMOUNT -> FieldError.AMOUNT_TOO_LARGE
            else -> null
        }
    }

    private fun rateError(text: String): FieldError? {
        if (text.isBlank()) return FieldError.REQUIRED
        val rate = ContractValidator.parseAmount(text) ?: return FieldError.INVALID_NUMBER
        return if (rate.signum() < 0 || rate > MAX_RATE) FieldError.INVALID_NUMBER else null
    }

    private fun intError(text: String, required: Boolean, max: Int): FieldError? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return if (required) FieldError.REQUIRED else null
        val value = trimmed.toIntOrNull() ?: return FieldError.INVALID_NUMBER
        return if (value < (if (required) 1 else 0) || value > max) FieldError.INVALID_NUMBER else null
    }

    private fun dateError(text: String) = if (text.isBlank()) FieldError.REQUIRED else FieldError.INVALID_DATE
}
