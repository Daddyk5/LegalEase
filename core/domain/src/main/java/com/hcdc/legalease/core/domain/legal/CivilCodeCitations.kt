package com.hcdc.legalease.core.domain.legal

import com.hcdc.legalease.core.domain.model.Citation
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ReasonCode

/**
 * Civil Code of the Philippines (Republic Act No. 386) references. The language model never
 * writes article numbers; the app attaches these by [ReasonCode] and contract type.
 *
 * Descriptions are short paraphrases for orientation, not legal advice. Have a lawyer review any
 * change to this table.
 */
object CivilCodeCitations {

    val REQUISITES = Citation(
        "Art. 1318",
        "A contract requires consent of the contracting parties, an object certain, and a cause of the obligation.",
    )
    val FORM = Citation(
        "Arts. 1356–1358",
        "Contracts bind in whatever form they are entered into, but certain contracts must appear in a public " +
            "document (e.g. acts on real rights over immovable property); the parties may compel each other to " +
            "observe that form.",
    )
    val RESCISSIBLE = Citation(
        "Arts. 1380–1389",
        "Rescissible contracts: valid contracts that may be rescinded for causes such as lesion of more than " +
            "one-fourth in contracts by guardians, or fraud on creditors.",
    )
    val VOIDABLE = Citation(
        "Arts. 1390–1402",
        "Voidable contracts: valid until annulled, where a party was incapable of giving consent or consent " +
            "was vitiated by mistake, violence, intimidation, undue influence, or fraud.",
    )
    val UNENFORCEABLE = Citation(
        "Arts. 1403–1408",
        "Unenforceable contracts, including those under the Statute of Frauds (Art. 1403(2)) that are not in " +
            "writing and subscribed by the party charged.",
    )
    val STATUTE_OF_FRAUDS = Citation(
        "Art. 1403(2)",
        "Statute of Frauds: certain agreements (e.g. sale of real property, leases longer than one year, " +
            "agreements not to be performed within a year) are unenforceable unless in writing and subscribed.",
    )
    val VOID = Citation(
        "Arts. 1409–1422",
        "Void contracts, including those with an illegal cause or object, those absolutely simulated or " +
            "fictitious, and those whose cause or object did not exist.",
    )
    val SALE = Citation(
        "Arts. 1458–1637",
        "Sales: obligations of the vendor and vendee, price, delivery, and warranties.",
    )
    val LEASE = Citation("Arts. 1642–1688", "Lease of things: rights and obligations of the lessor and lessee.")
    val LOAN = Citation(
        "Arts. 1933–1961",
        "Loan, including simple loan (mutuum); no interest is due unless expressly stipulated in writing (Art. 1956).",
    )

    fun forReason(code: ReasonCode): List<Citation> = when (code) {
        ReasonCode.NO_CONSENT -> listOf(REQUISITES)
        ReasonCode.NO_OBJECT, ReasonCode.NO_CAUSE -> listOf(REQUISITES, VOID)
        ReasonCode.FORM_NOT_MET -> listOf(FORM)
        ReasonCode.STATUTE_OF_FRAUDS -> listOf(STATUTE_OF_FRAUDS)
        ReasonCode.LESION, ReasonCode.FRAUD_ON_CREDITORS -> listOf(RESCISSIBLE)
        ReasonCode.VITIATED_CONSENT, ReasonCode.INCAPACITY -> listOf(VOIDABLE)
        ReasonCode.ILLEGAL_OBJECT, ReasonCode.SIMULATED -> listOf(VOID)
        ReasonCode.NONE -> emptyList()
    }

    fun forContractType(type: DetectedContractType): List<Citation> = when (type) {
        DetectedContractType.LEASE, DetectedContractType.LEASE_OF_PERSONAL_PROPERTY -> listOf(LEASE)
        DetectedContractType.LOAN -> listOf(LOAN)
        DetectedContractType.SALE -> listOf(SALE)
        DetectedContractType.OTHER -> emptyList()
    }

    fun forClassification(classification: Classification): Citation = when (classification) {
        Classification.VOID -> VOID
        Classification.VOIDABLE -> VOIDABLE
        Classification.UNENFORCEABLE -> UNENFORCEABLE
        Classification.RESCISSIBLE -> RESCISSIBLE
        Classification.ENFORCEABLE -> REQUISITES
    }
}
