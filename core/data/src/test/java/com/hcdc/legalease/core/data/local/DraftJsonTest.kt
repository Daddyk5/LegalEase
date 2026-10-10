package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.InterestPeriod
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftJsonTest {

    private val draft = ContractDraft(
        type = ContractType.LOAN,
        firstParty = Party("Juan", CivilStatus.WIDOWED, "Filipino", "Davao City", "Passport", "P1"),
        secondParty = Party("Maria", null, "Filipino", "Cebu City", "UMID", ""),
        partiesOfLegalAge = true,
        propertyKind = PropertyKind.PERSONAL,
        amount = "50,000",
        interestRate = "1.5",
        interestPeriod = InterestPeriod.MONTH,
        paymentSchedule = PaymentSchedule.MONTHLY_INSTALLMENTS,
        installments = "6",
        startDate = "2026-11-01",
        endDate = "2027-05-01",
        placeOfExecution = "Davao City",
        executionDate = "2026-11-01",
        witnessOne = "A",
        witnessTwo = "B",
        optionalClauses = listOf(OptionalClause("ai:1", "Notice", "Text.", OptionalClause.Source.ASSISTANT)),
    )

    @Test
    fun `every field round-trips`() {
        assertEquals(draft, DraftJson.decode(DraftJson.encode(draft)))
    }

    @Test
    fun `unreadable or unknown values give no draft rather than a guessed one`() {
        assertNull(DraftJson.decode("{broken"))
        assertNull(DraftJson.decode(DraftJson.encode(draft).replace("\"LOAN\"", "\"PROMISSORY_NOTE\"")))
    }
}
