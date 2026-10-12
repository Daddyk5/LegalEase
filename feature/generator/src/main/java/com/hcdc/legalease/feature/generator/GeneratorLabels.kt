package com.hcdc.legalease.feature.generator

import androidx.annotation.StringRes
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.ClauseRejection
import com.hcdc.legalease.core.domain.generator.FieldError
import com.hcdc.legalease.core.domain.generator.FormStep
import com.hcdc.legalease.core.domain.generator.LegalNotice
import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.InterestPeriod
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind
import com.hcdc.legalease.core.domain.result.AppError

// String resources for the generator's domain values, in one place. Exhaustive `when`s keep them
// complete when a value is added.

@get:StringRes
internal val ContractType.templateName: Int
    get() = when (this) {
        ContractType.LEASE -> R.string.generator_template_lease
        ContractType.LOAN -> R.string.generator_template_loan
        ContractType.SALE -> R.string.generator_template_sale
        ContractType.LEASE_OF_PERSONAL_PROPERTY -> R.string.generator_template_lease_personal
    }

/** Lessor/lessee, lender/borrower, or vendor/vendee. */
private val ContractType.roleNames: Pair<Int, Int>
    get() = when (this) {
        ContractType.LEASE, ContractType.LEASE_OF_PERSONAL_PROPERTY ->
            R.string.generator_role_lessor to R.string.generator_role_lessee
        ContractType.LOAN -> R.string.generator_role_lender to R.string.generator_role_borrower
        ContractType.SALE -> R.string.generator_role_vendor to R.string.generator_role_vendee
    }

@get:StringRes
internal val ContractType.amountLabel: Int
    get() = when (this) {
        ContractType.LEASE, ContractType.LEASE_OF_PERSONAL_PROPERTY -> R.string.generator_field_rent
        ContractType.SALE -> R.string.generator_field_price
        ContractType.LOAN -> R.string.generator_field_principal
    }

@StringRes
internal fun FormStep.title(type: ContractType): Int = when (this) {
    FormStep.FIRST_PARTY -> type.roleNames.first
    FormStep.SECOND_PARTY -> type.roleNames.second
    FormStep.SUBJECT -> R.string.generator_step_subject
    FormStep.TERMS -> R.string.generator_step_terms
    FormStep.SIGNING -> R.string.generator_step_signing
}

@StringRes
internal fun descriptionLabel(type: ContractType, kind: PropertyKind): Int = when (type) {
    ContractType.LEASE, ContractType.LOAN -> R.string.generator_field_premises
    ContractType.LEASE_OF_PERSONAL_PROPERTY -> R.string.generator_field_personal_lease
    ContractType.SALE ->
        if (kind == PropertyKind.REAL) R.string.generator_field_sale_real else R.string.generator_field_sale_personal
}

@get:StringRes
internal val CivilStatus.labelRes: Int
    get() = when (this) {
        CivilStatus.SINGLE -> R.string.generator_civil_single
        CivilStatus.MARRIED -> R.string.generator_civil_married
        CivilStatus.WIDOWED -> R.string.generator_civil_widowed
        CivilStatus.LEGALLY_SEPARATED -> R.string.generator_civil_separated
        CivilStatus.ANNULLED -> R.string.generator_civil_annulled
    }

@get:StringRes
internal val PropertyKind.labelRes: Int
    get() = when (this) {
        PropertyKind.REAL -> R.string.generator_property_real
        PropertyKind.PERSONAL -> R.string.generator_property_personal
    }

@get:StringRes
internal val InterestPeriod.labelRes: Int
    get() = when (this) {
        InterestPeriod.MONTH -> R.string.generator_interest_month
        InterestPeriod.YEAR -> R.string.generator_interest_year
    }

@get:StringRes
internal val PaymentSchedule.labelRes: Int
    get() = when (this) {
        PaymentSchedule.LUMP_SUM -> R.string.generator_schedule_lump
        PaymentSchedule.MONTHLY_INSTALLMENTS -> R.string.generator_schedule_monthly
    }

@get:StringRes
internal val PaperSize.labelRes: Int
    get() = when (this) {
        PaperSize.A4 -> R.string.generator_paper_a4
        PaperSize.LEGAL -> R.string.generator_paper_legal
    }

@get:StringRes
internal val LegalNotice.textRes: Int
    get() = when (this) {
        LegalNotice.REAL_PROPERTY_SALE_PUBLIC_DOCUMENT -> R.string.generator_notice_sale_real
        LegalNotice.LONG_LEASE_IN_WRITING -> R.string.generator_notice_long_lease
        LegalNotice.INTEREST_IN_WRITING -> R.string.generator_notice_interest
    }

@get:StringRes
internal val FieldError.textRes: Int
    get() = when (this) {
        FieldError.REQUIRED -> R.string.generator_error_required
        FieldError.INVALID_AMOUNT -> R.string.generator_error_amount
        FieldError.AMOUNT_TOO_LARGE -> R.string.generator_error_amount_large
        FieldError.INVALID_NUMBER -> R.string.generator_error_number
        FieldError.INVALID_DATE -> R.string.generator_error_date
        FieldError.END_NOT_AFTER_START -> R.string.generator_error_end_before_start
        FieldError.LEASE_OVER_99_YEARS -> R.string.generator_error_lease_99
        FieldError.EXECUTION_AFTER_START -> R.string.generator_error_execution_after_start
        FieldError.SAME_AS_OTHER_PARTY -> R.string.generator_error_same_party
        FieldError.WITNESS_IS_PARTY -> R.string.generator_error_witness_party
        FieldError.MUST_CONFIRM -> R.string.generator_error_legal_age
    }

/** A string resource, except [EditorMessage.SuggestionsWithheld], which is a plurals resource. */
internal val EditorMessage.textRes: Int
    get() = when (this) {
        is EditorMessage.SuggestionsWithheld -> R.plurals.generator_msg_withheld
        EditorMessage.NoSuggestions -> R.string.generator_msg_no_suggestions
        is EditorMessage.RewriteWithheld ->
            if (reason == ClauseRejection.CITES_LAW) {
                R.string.generator_msg_reword_cites_law
            } else {
                R.string.generator_msg_reword_withheld
            }
        EditorMessage.RewriteUnchanged -> R.string.generator_msg_unchanged
        is EditorMessage.Failed -> error.textRes
    }

@get:StringRes
private val AppError.textRes: Int
    get() = when {
        this == AppError.StorageFull -> R.string.generator_msg_storage
        this == AppError.DocumentNotFound -> R.string.generator_msg_not_found
        this == AppError.ModelUnavailable || this == AppError.ModelIncompatible ->
            R.string.generator_msg_assistant_failed
        this is AppError.Unexpected && detail.startsWith("llm_") -> R.string.generator_msg_assistant_failed
        else -> R.string.generator_msg_failed
    }

@get:StringRes
internal val Classification.labelRes: Int
    get() = when (this) {
        Classification.VOID -> R.string.generator_classification_void
        Classification.VOIDABLE -> R.string.generator_classification_voidable
        Classification.UNENFORCEABLE -> R.string.generator_classification_unenforceable
        Classification.RESCISSIBLE -> R.string.generator_classification_rescissible
        Classification.ENFORCEABLE -> R.string.generator_classification_enforceable
    }
