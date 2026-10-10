package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.generator.TemplateFormat.describe
import com.hcdc.legalease.core.domain.generator.TemplateFormat.titleCase
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.Party

/** One block of a rendered contract. */
data class ContractSection(
    val kind: Kind,
    val heading: String?,
    val paragraphs: List<String>,
    /** Set for [Kind.OPTIONAL] sections: the clause they render. */
    val optionalClauseId: String? = null,
) {
    enum class Kind {
        PREAMBLE,

        /** Fixed template text. Mandatory: neither the user nor the assistant can change it. */
        CLAUSE,

        /** A clause the user chose to add. */
        OPTIONAL,
        SIGNATURES,

        /** Always starts on a new page. */
        ACKNOWLEDGMENT,
    }

    val isMandatory: Boolean get() = kind != Kind.OPTIONAL
}

data class RenderedContract(val title: String, val sections: List<ContractSection>) {
    /** The contract as plain text: what is stored, synced, and analysed. */
    val plainText: String
        get() = buildString {
            append(title).append("\n\n")
            sections.forEach { section ->
                section.heading?.let { append(it).append("\n\n") }
                section.paragraphs.forEach { append(it).append("\n\n") }
            }
        }.trimEnd() + "\n"
}

/**
 * The four Philippine contract templates. Deterministic: the same draft always renders the same
 * text, and only the user's own entries and accepted optional clauses vary. The text states terms;
 * it does not cite law. The fixed clauses are in [ClauseTexts].
 */
object ContractTemplates {

    internal const val BLANK = "____________________"
    internal const val SIGNATURE_LINE = "________________________________"

    fun title(type: ContractType): String = when (type) {
        ContractType.LEASE -> "CONTRACT OF LEASE"
        ContractType.LOAN -> "CONTRACT OF LOAN"
        ContractType.SALE -> "DEED OF ABSOLUTE SALE"
        ContractType.LEASE_OF_PERSONAL_PROPERTY -> "CONTRACT OF LEASE OF PERSONAL PROPERTY"
    }

    /** The contract's name as used in running text. */
    fun shortName(type: ContractType) = when (type) {
        ContractType.LEASE -> "Contract of Lease"
        ContractType.LOAN -> "Contract of Loan"
        ContractType.SALE -> "Deed of Absolute Sale"
        ContractType.LEASE_OF_PERSONAL_PROPERTY -> "Contract of Lease of Personal Property"
    }

    /** "LESSOR" and "LESSEE", and so on. */
    fun roles(type: ContractType): Pair<String, String> = when (type) {
        ContractType.LEASE, ContractType.LEASE_OF_PERSONAL_PROPERTY -> "LESSOR" to "LESSEE"
        ContractType.LOAN -> "LENDER" to "BORROWER"
        ContractType.SALE -> "VENDOR" to "VENDEE"
    }

    /** Renders a valid draft. Returns null if [ContractValidator] rejects it. */
    fun render(draft: ContractDraft): RenderedContract? {
        val terms = ContractValidator.parse(draft) ?: return null
        val (first, second) = roles(draft.type)
        val context = TemplateContext(draft, terms, first, second)
        val body = when (draft.type) {
            ContractType.LEASE -> ClauseTexts.leaseOfRealProperty(context)
            ContractType.LEASE_OF_PERSONAL_PROPERTY -> ClauseTexts.leaseOfPersonalProperty(context)
            ContractType.LOAN -> ClauseTexts.loan(context)
            ContractType.SALE -> ClauseTexts.sale(context)
        }
        val numbered = number(body + draft.optionalClauses.map(::optional) + ClauseTexts.closing)
        return RenderedContract(
            title = title(draft.type),
            sections = listOf(preamble(context)) + numbered + signatures(context) + acknowledgment(draft),
        )
    }

    private fun preamble(c: TemplateContext) = ContractSection(
        kind = ContractSection.Kind.PREAMBLE,
        heading = null,
        paragraphs = listOf(
            "KNOW ALL PERSONS BY THESE PRESENTS:",
            "This ${shortName(c.draft.type)} is made and entered into this " +
                "${TemplateFormat.dayOf(c.terms.executionDate)} in ${c.draft.placeOfExecution.trim()}, " +
                "Philippines, by and between:",
            "${describe(c.draft.firstParty)}, hereinafter referred to as the \"${c.first}\";",
            "- and -",
            "${describe(c.draft.secondParty)}, hereinafter referred to as the \"${c.second}\".",
            "WITNESSETH:",
        ),
    )

    private fun signatures(c: TemplateContext) = ContractSection(
        kind = ContractSection.Kind.SIGNATURES,
        heading = null,
        paragraphs = listOf(
            "IN WITNESS WHEREOF, the parties have signed this ${shortName(c.draft.type)} on the date and at the " +
                "place first above written.",
            signatureBlock(c.draft.firstParty.name, titleCase(c.first)),
            signatureBlock(c.draft.secondParty.name, titleCase(c.second)),
            "SIGNED IN THE PRESENCE OF:",
            signatureBlock(c.draft.witnessOne, "Witness"),
            signatureBlock(c.draft.witnessTwo, "Witness"),
        ),
    )

    /** Notarial acknowledgment in the customary Philippine form; the notary completes the blanks. */
    private fun acknowledgment(d: ContractDraft) = ContractSection(
        kind = ContractSection.Kind.ACKNOWLEDGMENT,
        heading = "ACKNOWLEDGMENT",
        paragraphs = listOf(
            "REPUBLIC OF THE PHILIPPINES )\n$BLANK ) S.S.",
            "BEFORE ME, a Notary Public for and in $BLANK, this ______ day of $BLANK, ______, personally appeared:",
            identity(d.firstParty),
            identity(d.secondParty),
            "known to me and to me known to be the same persons who executed the foregoing ${shortName(d.type)}, " +
                "and they acknowledged to me that the same is their free and voluntary act and deed.",
            "This instrument, consisting of ______ pages, including the page on which this acknowledgment is " +
                "written, has been signed by the parties and their instrumental witnesses on each and every page.",
            "WITNESS MY HAND AND SEAL on the date and at the place first above written.",
            "$SIGNATURE_LINE\nNotary Public",
            "Doc. No. ______;\nPage No. ______;\nBook No. ______;\nSeries of ______.",
        ),
    )

    private fun optional(c: OptionalClause) = ContractSection(
        kind = ContractSection.Kind.OPTIONAL,
        heading = c.title.trim().uppercase(),
        paragraphs = listOf(c.text.trim()),
        optionalClauseId = c.id,
    )

    private fun number(sections: List<ContractSection>) =
        sections.mapIndexed { i, s -> s.copy(heading = "${i + 1}. ${s.heading}") }
}

private fun signatureBlock(name: String, role: String) =
    "${ContractTemplates.SIGNATURE_LINE}\n${name.trim().uppercase()}\n$role"

private fun identity(p: Party): String {
    val number = p.idNumber.trim().ifEmpty { "______________" }
    val blank = ContractTemplates.BLANK
    return "${p.name.trim().uppercase()} — ${p.idType.trim()} No. $number, issued on $blank at $blank"
}
