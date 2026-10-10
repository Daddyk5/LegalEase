package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.InterestPeriod
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

internal fun validDraft(type: ContractType) = ContractDraft(
    type = type,
    firstParty = Party(
        "Juan dela Cruz", CivilStatus.MARRIED, "Filipino", "12 Mabini St., Davao City", "Passport", "P1234567A",
    ),
    secondParty = Party("Maria Santos", CivilStatus.SINGLE, "Filipino", "34 Rizal Ave., Davao City", "UMID", ""),
    partiesOfLegalAge = true,
    propertyKind = PropertyKind.REAL,
    propertyDescription = "A house and lot at 56 Bonifacio St., Davao City, covered by TCT No. T-12345",
    amount = "15,000",
    depositMonths = "2",
    interestRate = "5",
    interestPeriod = InterestPeriod.YEAR,
    paymentSchedule = PaymentSchedule.LUMP_SUM,
    startDate = "2026-11-01",
    endDate = "2027-10-31",
    placeOfExecution = "Davao City",
    executionDate = "2026-10-15",
    witnessOne = "Pedro Reyes",
    witnessTwo = "Ana Lim",
)

class ContractTemplatesTest {

    @Test
    fun `every template renders the parties, a notarial acknowledgment, and witness blocks`() {
        ContractType.entries.forEach { type ->
            val rendered = ContractTemplates.render(validDraft(type))
            assertNotNull(type.name, rendered)
            val text = rendered!!.plainText
            assertTrue(type.name, text.startsWith(ContractTemplates.title(type)))
            assertTrue(type.name, "JUAN DELA CRUZ, of legal age, married, Filipino" in text)
            assertTrue(type.name, "SIGNED IN THE PRESENCE OF:" in text)
            assertTrue(type.name, "PEDRO REYES\nWitness" in text && "ANA LIM\nWitness" in text)
            assertEquals(type.name, ContractSection.Kind.ACKNOWLEDGMENT, rendered.sections.last().kind)
            assertTrue(type.name, "Passport No. P1234567A" in text)
            // A blank ID number is left for the notary, never invented.
            assertTrue(type.name, "UMID No. ______________" in text)
        }
    }

    @Test
    fun `rendering is deterministic`() {
        val draft = validDraft(ContractType.LEASE)
        assertEquals(ContractTemplates.render(draft), ContractTemplates.render(draft))
    }

    @Test
    fun `templates state terms but never cite law`() {
        ContractType.entries.forEach { type ->
            val text = ContractTemplates.render(validDraft(type))!!.plainText
            assertFalse(type.name, Regex("""(?i)\bart(icle)?s?\.?\s*\d""").containsMatchIn(text))
            assertFalse(type.name, "Civil Code" in text)
        }
    }

    @Test
    fun `lease states rent in words and figures, the term, and the deposit`() {
        val text = ContractTemplates.render(validDraft(ContractType.LEASE))!!.plainText
        assertTrue("FIFTEEN THOUSAND PESOS (PHP 15,000.00)" in text)
        assertTrue("beginning November 1, 2026 and ending October 31, 2027" in text)
        assertTrue("THIRTY THOUSAND PESOS (PHP 30,000.00), equivalent to two (2) months of rent" in text)
        assertTrue("this 15th day of October 2026 in Davao City, Philippines" in text)
    }

    @Test
    fun `no deposit clause when no deposit is given`() {
        val text = ContractTemplates.render(validDraft(ContractType.LEASE).copy(depositMonths = ""))!!.plainText
        assertFalse("SECURITY DEPOSIT" in text)
    }

    @Test
    fun `loan writes the interest out expressly, or says there is none`() {
        val withInterest = ContractTemplates.render(validDraft(ContractType.LOAN))!!.plainText
        assertTrue("interest at the rate of five percent (5%) per annum" in withInterest)
        assertTrue("expressly stipulate this interest in writing" in withInterest)
        val noInterest = ContractTemplates.render(validDraft(ContractType.LOAN).copy(interestRate = "0"))!!.plainText
        assertTrue("The loan shall bear no interest." in noInterest)
        assertFalse("together with the interest due" in noInterest)
    }

    @Test
    fun `loan in installments states the count and the due date`() {
        val draft = validDraft(ContractType.LOAN)
            .copy(paymentSchedule = PaymentSchedule.MONTHLY_INSTALLMENTS, installments = "12")
        val text = ContractTemplates.render(draft)!!.plainText
        assertTrue("in twelve (12) monthly installments" in text)
        assertTrue("on or before October 31, 2027 (the \"Due Date\")" in text)
    }

    @Test
    fun `optional clauses are numbered before the closing clause and marked optional`() {
        val clause = OptionalClause("lib:no_pets", "Pets", "No pets.", OptionalClause.Source.LIBRARY)
        val rendered = ContractTemplates.render(validDraft(ContractType.LEASE).copy(optionalClauses = listOf(clause)))!!
        val numbered = rendered.sections
            .filter { it.kind == ContractSection.Kind.CLAUSE || it.kind == ContractSection.Kind.OPTIONAL }
        val optional = numbered[numbered.size - 2]
        assertEquals(ContractSection.Kind.OPTIONAL, optional.kind)
        assertFalse(optional.isMandatory)
        assertEquals("lib:no_pets", optional.optionalClauseId)
        assertTrue(numbered.last().heading!!.endsWith("ENTIRE AGREEMENT"))
        assertTrue(numbered.mapIndexed { i, s -> s.heading!!.startsWith("${i + 1}. ") }.all { it })
    }

    @Test
    fun `invalid drafts do not render`() {
        assertNull(ContractTemplates.render(validDraft(ContractType.SALE).copy(amount = "")))
    }

    @Test
    fun `ordinals`() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "31st"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 31).map(TemplateFormat::ordinal))
    }
}

class ContractValidatorTest {

    @Test
    fun `a complete draft is valid for every type`() {
        ContractType.entries.forEach {
            assertEquals(it.name, emptyMap<FormField, FieldError>(), ContractValidator.validateAll(validDraft(it)))
        }
    }

    @Test
    fun `required party fields`() {
        val draft = validDraft(ContractType.LEASE).copy(firstParty = Party(citizenship = ""))
        val errors = ContractValidator.validate(draft, FormStep.FIRST_PARTY)
        assertEquals(
            setOf(
                FormField.FIRST_NAME, FormField.FIRST_CIVIL_STATUS, FormField.FIRST_CITIZENSHIP,
                FormField.FIRST_ADDRESS, FormField.FIRST_ID_TYPE,
            ),
            errors.keys,
        )
        assertTrue(errors.values.all { it == FieldError.REQUIRED })
    }

    @Test
    fun `parties must differ and confirm legal age`() {
        val draft = validDraft(ContractType.LOAN).let {
            it.copy(secondParty = it.secondParty.copy(name = "  juan  DELA cruz "), partiesOfLegalAge = false)
        }
        val errors = ContractValidator.validate(draft, FormStep.SECOND_PARTY)
        assertEquals(FieldError.SAME_AS_OTHER_PARTY, errors[FormField.SECOND_NAME])
        assertEquals(FieldError.MUST_CONFIRM, errors[FormField.PARTIES_OF_LEGAL_AGE])
    }

    @Test
    fun `amounts must be positive pesos with at most two decimals`() {
        fun amountError(amount: String) =
            ContractValidator.validate(validDraft(ContractType.SALE).copy(amount = amount), FormStep.TERMS)
                .get(FormField.AMOUNT)
        assertNull(amountError("1,500,000.50"))
        assertEquals(FieldError.REQUIRED, amountError(" "))
        assertEquals(FieldError.INVALID_AMOUNT, amountError("0"))
        assertEquals(FieldError.INVALID_AMOUNT, amountError("-5"))
        assertEquals(FieldError.INVALID_AMOUNT, amountError("12.345"))
        assertEquals(FieldError.INVALID_AMOUNT, amountError("12abc"))
        assertEquals(FieldError.AMOUNT_TOO_LARGE, amountError("2000000000000"))
    }

    @Test
    fun `date logic`() {
        val lease = validDraft(ContractType.LEASE)
        fun termErrors(draft: ContractDraft) = ContractValidator.validate(draft, FormStep.TERMS)
        assertEquals(FieldError.INVALID_DATE, termErrors(lease.copy(startDate = "2026-02-30"))[FormField.START_DATE])
        assertEquals(FieldError.END_NOT_AFTER_START, termErrors(lease.copy(endDate = "2026-11-01"))[FormField.END_DATE])
        assertEquals(FieldError.LEASE_OVER_99_YEARS, termErrors(lease.copy(endDate = "2125-11-02"))[FormField.END_DATE])
        assertNull(termErrors(lease.copy(endDate = "2125-11-01"))[FormField.END_DATE])
        val late = ContractValidator.validate(lease.copy(executionDate = "2026-11-02"), FormStep.SIGNING)
        assertEquals(FieldError.EXECUTION_AFTER_START, late[FormField.EXECUTION_DATE])
    }

    @Test
    fun `a sale needs no term dates`() {
        val sale = validDraft(ContractType.SALE).copy(startDate = "", endDate = "")
        assertEquals(emptyMap<FormField, FieldError>(), ContractValidator.validateAll(sale))
        assertNull(ContractValidator.parse(sale)!!.start)
    }

    @Test
    fun `loan interest must be written, zero allowed`() {
        val loan = validDraft(ContractType.LOAN)
        fun rateError(rate: String) =
            ContractValidator.validate(loan.copy(interestRate = rate), FormStep.TERMS)[FormField.INTEREST_RATE]
        assertEquals(FieldError.REQUIRED, rateError(""))
        assertEquals(FieldError.INVALID_NUMBER, rateError("101"))
        assertNull(rateError("0"))
        val installments = loan.copy(paymentSchedule = PaymentSchedule.MONTHLY_INSTALLMENTS, installments = "0")
        val errors = ContractValidator.validate(installments, FormStep.TERMS)
        assertEquals(FieldError.INVALID_NUMBER, errors[FormField.INSTALLMENTS])
    }

    @Test
    fun `witnesses are required and cannot be a party`() {
        val errors = ContractValidator.validate(
            validDraft(ContractType.SALE).copy(witnessOne = "", witnessTwo = "Maria Santos"),
            FormStep.SIGNING,
        )
        assertEquals(FieldError.REQUIRED, errors[FormField.WITNESS_ONE])
        assertEquals(FieldError.WITNESS_IS_PARTY, errors[FormField.WITNESS_TWO])
    }

    @Test
    fun `loans have no property step`() {
        assertFalse(FormStep.SUBJECT in ContractValidator.steps(ContractType.LOAN))
        assertTrue(FormStep.SUBJECT in ContractValidator.steps(ContractType.SALE))
    }
}

class LegalNoticesTest {

    @Test
    fun `sale of real property needs a public document, personal property does not`() {
        val sale = validDraft(ContractType.SALE)
        assertEquals(listOf(LegalNotice.REAL_PROPERTY_SALE_PUBLIC_DOCUMENT), LegalNotices.forDraft(sale))
        assertEquals(emptyList<LegalNotice>(), LegalNotices.forDraft(sale.copy(propertyKind = PropertyKind.PERSONAL)))
    }

    @Test
    fun `lease of real property longer than one year falls under the Statute of Frauds`() {
        val oneYear = validDraft(ContractType.LEASE).copy(startDate = "2026-11-01", endDate = "2027-11-01")
        assertEquals(emptyList<LegalNotice>(), LegalNotices.forDraft(oneYear))
        val longer = oneYear.copy(endDate = "2027-11-02")
        assertEquals(listOf(LegalNotice.LONG_LEASE_IN_WRITING), LegalNotices.forDraft(longer))
        // Not for personal property.
        val personal = longer.copy(type = ContractType.LEASE_OF_PERSONAL_PROPERTY)
        assertEquals(emptyList<LegalNotice>(), LegalNotices.forDraft(personal))
    }

    @Test
    fun `interest-bearing loans get the written-interest notice`() {
        assertEquals(listOf(LegalNotice.INTEREST_IN_WRITING), LegalNotices.forDraft(validDraft(ContractType.LOAN)))
        val free = validDraft(ContractType.LOAN).copy(interestRate = "0")
        assertEquals(emptyList<LegalNotice>(), LegalNotices.forDraft(free))
    }
}

class PesoAmountTest {

    @Test
    fun `words and figures`() {
        assertEquals("FIFTEEN THOUSAND PESOS (PHP 15,000.00)", PesoAmount.format(BigDecimal("15000")))
        assertEquals("ONE PESO (PHP 1.00)", PesoAmount.format(BigDecimal("1")))
        assertEquals("ONE HUNDRED PESOS AND 50/100 (PHP 100.50)", PesoAmount.format(BigDecimal("100.5")))
        assertEquals(
            "ONE MILLION TWO HUNDRED THIRTY-FOUR THOUSAND FIVE HUNDRED SIXTY-SEVEN PESOS (PHP 1,234,567.00)",
            PesoAmount.format(BigDecimal("1234567")),
        )
        assertEquals("ONE BILLION ONE PESOS (PHP 1,000,000,001.00)", PesoAmount.format(BigDecimal("1000000001")))
        assertEquals("NINETY PESOS AND 05/100 (PHP 90.05)", PesoAmount.format(BigDecimal("90.05")))
    }
}

class WordDiffTest {

    @Test
    fun `marks added and removed words`() {
        val diff = WordDiff.diff("The lessee shall not keep pets", "The lessee may not keep any pets")
        assertEquals(
            listOf(
                DiffSegment(DiffSegment.Type.SAME, "The lessee"),
                DiffSegment(DiffSegment.Type.ADDED, "may"),
                DiffSegment(DiffSegment.Type.REMOVED, "shall"),
                DiffSegment(DiffSegment.Type.SAME, "not keep"),
                DiffSegment(DiffSegment.Type.ADDED, "any"),
                DiffSegment(DiffSegment.Type.SAME, "pets"),
            ),
            diff,
        )
    }

    @Test
    fun `a new clause is all additions, and spacing alone is not a change`() {
        assertEquals(listOf(DiffSegment(DiffSegment.Type.ADDED, "No pets.")), WordDiff.diff("", "No pets."))
        assertFalse(WordDiff.changes("No  pets.", " No pets. "))
    }
}

class ClauseGuardTest {

    @Test
    fun `suggestions citing law are withheld`() {
        listOf(
            "As provided by law, the lessee shall pay.",
            "Under Article 1659 the lessor may rescind.",
            "Pursuant to Art. 1956 interest applies.",
            "Subject to R.A. 9653 rent control.",
            "Consistent with the Civil Code.",
            "Following Supreme Court rulings.",
        ).forEach { assertEquals(it, ClauseRejection.CITES_LAW, ClauseGuard.checkSuggestion("Title", it)) }
    }

    @Test
    fun `ordinary words that contain 'law' are fine`() {
        assertNull(ClauseGuard.checkSuggestion("Use", "The LESSEE shall use the Premises only for lawful purposes."))
    }

    @Test
    fun `suggestions may not set amounts or dates`() {
        listOf(
            "A penalty of PHP 500 applies.",
            "A penalty of 500 pesos applies.",
            "Starting January 5 the lease renews.",
        ).forEach { assertEquals(it, ClauseRejection.STATES_AMOUNT_OR_DATE, ClauseGuard.checkSuggestion("Term", it)) }
        assertNull(ClauseGuard.checkSuggestion("Notice", "Give thirty (30) days' notice."))
    }

    @Test
    fun `rewrites must keep every number and party`() {
        val original = "The LESSEE shall give the LESSOR thirty (30) days' notice."
        assertNull(ClauseGuard.checkRewrite(original, "The LESSEE must notify the LESSOR thirty (30) days in advance."))
        val sixty = "The LESSEE must notify the LESSOR sixty (60) days ahead."
        assertEquals(ClauseRejection.CHANGES_NUMBERS, ClauseGuard.checkRewrite(original, sixty))
        val noLessor = "The LESSEE must give thirty (30) days' notice."
        assertEquals(ClauseRejection.CHANGES_PARTIES, ClauseGuard.checkRewrite(original, noLessor))
        val law = "The LESSEE shall give the LESSOR thirty (30) days' notice as required by law."
        assertEquals(ClauseRejection.CITES_LAW, ClauseGuard.checkRewrite(original, law))
        assertEquals(ClauseRejection.EMPTY, ClauseGuard.checkRewrite(original, "  "))
    }

    @Test
    fun `overlong output is withheld`() {
        val long = List(ClauseGuard.MAX_CLAUSE_WORDS + 1) { "word" }.joinToString(" ")
        assertEquals(ClauseRejection.TOO_LONG, ClauseGuard.checkSuggestion("Title", long))
        val longTitle = "x".repeat(ClauseGuard.MAX_TITLE_CHARS + 1)
        assertEquals(ClauseRejection.TOO_LONG, ClauseGuard.checkSuggestion(longTitle, "Fine."))
    }
}

class OptionalClauseLibraryTest {

    @Test
    fun `library clauses pass the same guard as assistant output`() {
        ContractType.entries.forEach { type ->
            listOf(PropertyKind.REAL, PropertyKind.PERSONAL).forEach { kind ->
                OptionalClauseLibrary.forDraft(validDraft(type).copy(propertyKind = kind)).forEach {
                    assertNull("${type.name} ${it.id}", ClauseGuard.checkSuggestion(it.title, it.text))
                    assertTrue(it.id.startsWith("lib:"))
                }
            }
        }
    }
}
