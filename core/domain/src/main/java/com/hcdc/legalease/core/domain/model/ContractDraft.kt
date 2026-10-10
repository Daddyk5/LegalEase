package com.hcdc.legalease.core.domain.model

/** Civil status as written in Philippine contracts ("of legal age, single, Filipino"). */
enum class CivilStatus(val label: String) {
    SINGLE("single"),
    MARRIED("married"),
    WIDOWED("widowed"),
    LEGALLY_SEPARATED("legally separated"),
    ANNULLED("annulled"),
}

/** One contracting party. [idType] and [idNumber] become the competent evidence of identity in the acknowledgment. */
data class Party(
    val name: String = "",
    val civilStatus: CivilStatus? = null,
    val citizenship: String = "Filipino",
    val address: String = "",
    val idType: String = "",
    val idNumber: String = "",
)

/** For a Deed of Absolute Sale: land and buildings, or movables such as a vehicle. */
enum class PropertyKind { REAL, PERSONAL }

enum class InterestPeriod(val label: String) { MONTH("per month"), YEAR("per annum") }

enum class PaymentSchedule { LUMP_SUM, MONTHLY_INSTALLMENTS }

/** An optional clause the user chose to include. Never part of the mandatory template text. */
data class OptionalClause(
    val id: String,
    val title: String,
    val text: String,
    val source: Source,
) {
    enum class Source {
        /** From LegalEase's fixed clause library. */
        LIBRARY,

        /** Suggested or rewritten by the on-device assistant and accepted by the user after reviewing the diff. */
        ASSISTANT,
    }
}

/**
 * Everything the user typed for one contract. Amounts and dates stay as the typed strings so the
 * form can show exactly what was entered; [com.hcdc.legalease.core.domain.generator.ContractValidator]
 * parses them. Fields a template does not use are ignored by it.
 */
data class ContractDraft(
    val type: ContractType,
    /** Lessor, lender, or vendor. */
    val firstParty: Party = Party(),
    /** Lessee, borrower, or vendee. */
    val secondParty: Party = Party(),
    /** The templates describe both parties as "of legal age"; the user must confirm it. */
    val partiesOfLegalAge: Boolean = false,
    val propertyKind: PropertyKind = PropertyKind.REAL,
    val propertyDescription: String = "",
    /** Rent per month, purchase price, or loan principal, in pesos. */
    val amount: String = "",
    /** Months of rent held as a security deposit; blank for none. */
    val depositMonths: String = "",
    val interestRate: String = "",
    val interestPeriod: InterestPeriod = InterestPeriod.YEAR,
    val paymentSchedule: PaymentSchedule = PaymentSchedule.LUMP_SUM,
    val installments: String = "",
    /** Lease start, or loan release date (ISO yyyy-MM-dd). */
    val startDate: String = "",
    /** Lease end, or loan due date (ISO yyyy-MM-dd). */
    val endDate: String = "",
    val placeOfExecution: String = "",
    val executionDate: String = "",
    val witnessOne: String = "",
    val witnessTwo: String = "",
    val optionalClauses: List<OptionalClause> = emptyList(),
)
