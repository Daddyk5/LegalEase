package com.hcdc.legalease.core.domain.generator

import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.PropertyKind

/**
 * Formalities the user must know about before signing. The UI shows each as fixed, reviewed text
 * with its citation; the citations are never generated.
 */
enum class LegalNotice {
    /** A sale of real property must appear in a public (notarized) document: Art. 1358. */
    REAL_PROPERTY_SALE_PUBLIC_DOCUMENT,

    /** Statute of Frauds, Art. 1403(2)(e): a lease of real property for more than one year must be in writing. */
    LONG_LEASE_IN_WRITING,

    /** Art. 1956: interest is due only if expressly stipulated in writing. */
    INTEREST_IN_WRITING,
}

object LegalNotices {

    fun forDraft(draft: ContractDraft): List<LegalNotice> = buildList {
        if (draft.type == ContractType.SALE && draft.propertyKind == PropertyKind.REAL) {
            add(LegalNotice.REAL_PROPERTY_SALE_PUBLIC_DOCUMENT)
        }
        if (draft.type == ContractType.LEASE) {
            val start = ContractValidator.parseDate(draft.startDate)
            val end = ContractValidator.parseDate(draft.endDate)
            if (start != null && end != null && end.isAfter(start.plusYears(1))) add(LegalNotice.LONG_LEASE_IN_WRITING)
        }
        if (draft.type == ContractType.LOAN) {
            val rate = ContractValidator.parseAmount(draft.interestRate)
            if (rate != null && rate.signum() > 0) add(LegalNotice.INTEREST_IN_WRITING)
        }
    }
}
