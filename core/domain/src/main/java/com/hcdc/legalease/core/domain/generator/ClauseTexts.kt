package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.generator.TemplateFormat.count
import com.hcdc.legalease.core.domain.generator.TemplateFormat.date
import com.hcdc.legalease.core.domain.generator.TemplateFormat.dueDay
import com.hcdc.legalease.core.domain.generator.TemplateFormat.percent
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind
import java.math.BigDecimal

/** What every clause needs: the user's draft, its parsed terms, and the role words for the parties. */
internal data class TemplateContext(
    val draft: ContractDraft,
    val terms: ValidatedTerms,
    /** LESSOR, LENDER, or VENDOR. */
    val first: String,
    /** LESSEE, BORROWER, or VENDEE. */
    val second: String,
) {
    val description: String get() = draft.propertyDescription.trim()
    val amount: String get() = PesoAmount.format(terms.amount)
}

/** The fixed clauses of each template, in order. */
internal object ClauseTexts {

    fun leaseOfRealProperty(c: TemplateContext) = with(c) {
        listOfNotNull(
            clause(
                "PREMISES",
                "The $first hereby leases to the $second the following property: $description (the \"Premises\").",
            ),
            term(c),
            rent(c),
            deposit(c, "Premises"),
            clause(
                "USE",
                "The $second shall use the Premises with the diligence of a good father of a family, only for " +
                    "lawful purposes, and in compliance with applicable regulations.",
            ),
            clause(
                "UTILITIES",
                "Charges for electricity, water, and other utilities consumed in the Premises during the lease " +
                    "shall be paid by the $second.",
            ),
            clause(
                "REPAIRS AND IMPROVEMENTS",
                "The $first shall make the necessary repairs to keep the Premises suitable for the use for which " +
                    "it is leased. The $second shall inform the $first of any needed repair at the earliest " +
                    "opportunity and shall make no alterations or improvements without the $first's prior " +
                    "written consent.",
            ),
            noAssignment(c, "Premises"),
            clause(
                "RETURN OF THE PREMISES",
                "At the end of the lease, the $second shall vacate and return the Premises to the $first in the " +
                    "condition in which it was received, ordinary wear and tear excepted.",
            ),
        )
    }

    fun leaseOfPersonalProperty(c: TemplateContext) = with(c) {
        listOfNotNull(
            clause(
                "PROPERTY",
                "The $first hereby leases to the $second the following property: $description (the \"Property\").",
            ),
            term(c),
            rent(c),
            deposit(c, "Property"),
            clause(
                "DELIVERY AND USE",
                "The $first shall deliver the Property to the $second in good working condition at the start of " +
                    "the lease. The $second shall use the Property with the diligence of a good father of a family, " +
                    "only for lawful purposes, and only for the use for which it is leased.",
            ),
            clause(
                "MAINTENANCE",
                "The $second shall bear the cost of ordinary maintenance during the lease. Repairs not caused by " +
                    "the $second's fault or negligence shall be for the account of the $first.",
            ),
            clause(
                "LOSS OR DAMAGE",
                "The $second shall be responsible for the loss of, or damage to, the Property during the lease, " +
                    "unless it occurred without the $second's fault.",
            ),
            noAssignment(c, "Property"),
            clause(
                "RETURN OF THE PROPERTY",
                "At the end of the lease, the $second shall return the Property to the $first in the condition in " +
                    "which it was received, ordinary wear and tear excepted.",
            ),
        )
    }

    fun loan(c: TemplateContext): List<ContractSection> = with(c) {
        val release = terms.start!!
        val rate = terms.interestRatePercent!!
        val free = rate.signum() == 0
        val delivered = if (release == terms.executionDate) {
            "The $first has lent and delivered to the $second, who acknowledges receipt of it, the sum of " +
                "$amount (the \"Principal\")."
        } else {
            "The $first shall lend and deliver to the $second on ${date(release)} the sum of " +
                "$amount (the \"Principal\")."
        }
        val interest = if (free) {
            "The loan shall bear no interest."
        } else {
            "The Principal shall earn interest at the rate of ${percent(rate)} ${draft.interestPeriod.label}, " +
                "computed from ${date(release)}. The parties expressly stipulate this interest in writing."
        }
        listOf(
            clause("LOAN", delivered),
            clause("INTEREST", interest),
            clause("PAYMENT", payment(c, interestDue = if (free) "" else ", together with the interest due,")),
            clause(
                "PLACE AND MANNER OF PAYMENT",
                "Payments shall be made to the $first at the address stated above, or in any other manner the " +
                    "$first designates in writing. The $first shall issue a receipt for every payment.",
            ),
            clause(
                "PREPAYMENT",
                "The $second may pay the loan, in whole or in part, before the Due Date" +
                    if (free) "." else ", with interest computed only up to the date of payment.",
            ),
            clause(
                "DEFAULT",
                "If the $second fails to pay any amount when due, the $first may demand payment of the entire " +
                    "unpaid balance.",
            ),
        )
    }

    fun sale(c: TemplateContext): List<ContractSection> = with(c) {
        val delivery = when (draft.propertyKind) {
            PropertyKind.REAL ->
                "The $first shall deliver to the $second possession of the Property and all documents of title " +
                    "to it upon the signing of this Deed."
            PropertyKind.PERSONAL ->
                "The $first shall deliver the Property to the $second, together with all documents of ownership " +
                    "and registration, upon the signing of this Deed."
        }
        listOf(
            clause(
                "THE PROPERTY",
                "The $first is the absolute owner of the following property: $description (the \"Property\").",
            ),
            clause(
                "SALE",
                "For and in consideration of the sum of $amount, receipt of which in full is " +
                    "hereby acknowledged by the $first, the $first hereby SELLS, TRANSFERS, and CONVEYS absolutely " +
                    "to the $second, and to the $second's heirs and assigns, the Property.",
            ),
            clause(
                "WARRANTIES",
                "The $first warrants that the Property is free from all liens and encumbrances, that the $first " +
                    "has the right to sell it, and that the $first will defend the $second's title to it against " +
                    "the claims of any person.",
            ),
            clause("DELIVERY", delivery),
        )
    }

    /** Always the last numbered clause. */
    val closing = clause(
        "ENTIRE AGREEMENT",
        "This contract contains the entire agreement of the parties. Any amendment shall be in writing " +
            "and signed by both parties.",
    )

    private fun payment(c: TemplateContext, interestDue: String): String = with(c) {
        val due = "on or before ${date(terms.end!!)} (the \"Due Date\")."
        when (draft.paymentSchedule) {
            PaymentSchedule.LUMP_SUM -> "The $second shall pay the Principal$interestDue in full $due"
            PaymentSchedule.MONTHLY_INSTALLMENTS ->
                "The $second shall pay the Principal$interestDue in ${count(terms.installments!!)} monthly " +
                    "installments, ${dueDay(terms.start!!)} of each month, with the entire balance due $due"
        }
    }

    private fun rent(c: TemplateContext) = clause(
        "RENT",
        "The ${c.second} shall pay a monthly rent of ${c.amount}, in advance, " +
            "${dueDay(c.terms.start!!)} of each month, without need of demand.",
    )

    private fun term(c: TemplateContext) = clause(
        "TERM",
        "The lease shall be for a period beginning ${date(c.terms.start!!)} and ending ${date(c.terms.end!!)}, " +
            "unless sooner terminated as provided in this contract. Any renewal shall be agreed in writing by the " +
            "parties; continued possession by the ${c.second} does not renew the lease for the same period.",
    )

    private fun deposit(c: TemplateContext, thing: String): ContractSection? = with(c) {
        val months = terms.depositMonths?.takeIf { it > 0 } ?: return null
        clause(
            "SECURITY DEPOSIT",
            "Upon signing, the $second shall pay a security deposit of " +
                "${PesoAmount.format(terms.amount.multiply(BigDecimal(months)))}, equivalent to ${count(months)} " +
                "month${if (months == 1) "" else "s"} of rent, to answer for unpaid rent, unpaid charges, and " +
                "damage to the $thing beyond ordinary wear and tear. The $first shall return the balance within " +
                "thirty (30) days after the end of the lease and the return of the $thing. The deposit shall not " +
                "be applied to rent without the $first's written consent.",
        )
    }

    private fun noAssignment(c: TemplateContext, thing: String) = clause(
        "ASSIGNMENT AND SUBLEASE",
        "The ${c.second} shall not assign this lease, or sublease the $thing in whole or in part, without the " +
            "${c.first}'s prior written consent.",
    )

    private fun clause(heading: String, text: String) =
        ContractSection(ContractSection.Kind.CLAUSE, heading, listOf(text))
}
