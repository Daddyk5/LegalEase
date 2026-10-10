package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.PropertyKind

/** Fixed optional clauses the user may add. Written by hand, not generated, and free of legal citations. */
object OptionalClauseLibrary {

    fun forDraft(draft: ContractDraft): List<OptionalClause> {
        val (first, second) = ContractTemplates.roles(draft.type)
        val specific = when (draft.type) {
            ContractType.LEASE -> lease(first, second)
            ContractType.LEASE_OF_PERSONAL_PROPERTY -> personalLease(first, second)
            ContractType.LOAN -> loan(first, second)
            ContractType.SALE -> sale(draft.propertyKind, first, second)
        }
        return specific + common(draft.placeOfExecution.trim())
    }

    private fun common(place: String) = listOfNotNull(
        clause(
            "notice_of_address",
            "Change of address",
            "Each party shall inform the other in writing of any change of address. Notices sent to the last " +
                "address so given are deemed received.",
        ),
        place.takeIf { it.isNotEmpty() }?.let {
            clause(
                "venue",
                "Venue",
                "Any action arising from this contract shall be filed only in the proper courts of $it.",
            )
        },
    )

    private fun lease(lessor: String, lessee: String) = listOf(
        clause(
            "no_pets",
            "Pets",
            "The $lessee shall not keep pets in the Premises without the $lessor's prior written consent.",
        ),
        clause(
            "inspection",
            "Inspection",
            "The $lessor may inspect the Premises at reasonable hours after giving the $lessee at least " +
                "twenty-four (24) hours' notice.",
        ),
        clause(
            "pre_termination",
            "Pre-termination",
            "Either party may end this lease before its expiration by giving the other at least thirty (30) " +
                "days' written notice.",
        ),
    )

    private fun personalLease(lessor: String, lessee: String) = listOf(
        clause(
            "authorized_users",
            "Authorized users",
            "Only the $lessee, or persons the $lessor approves in writing, may use or operate the Property.",
        ),
        clause(
            "insurance",
            "Insurance",
            "The $lessee shall keep the Property insured against loss and damage during the lease, with the " +
                "$lessor named as beneficiary.",
        ),
    )

    private fun loan(lender: String, borrower: String) = listOf(
        clause(
            "notice_before_acceleration",
            "Notice before demand",
            "Before demanding the entire unpaid balance, the $lender shall give the $borrower written notice of " +
                "the missed payment and at least fifteen (15) days to pay it.",
        ),
    )

    private fun sale(kind: PropertyKind, vendor: String, vendee: String) = when (kind) {
        PropertyKind.REAL -> listOf(
            clause(
                "taxes_and_expenses",
                "Taxes and expenses",
                "The capital gains tax and documentary stamp tax on this sale shall be paid by the $vendor. " +
                    "The transfer tax and the registration fees shall be paid by the $vendee.",
            ),
        )
        PropertyKind.PERSONAL -> listOf(
            clause(
                "inspected",
                "Inspection by the ${vendee.lowercase()}",
                "The $vendee has inspected the Property and accepts it in its present condition.",
            ),
        )
    }

    private fun clause(id: String, title: String, text: String) =
        OptionalClause(id = "lib:$id", title = title, text = text, source = OptionalClause.Source.LIBRARY)
}
