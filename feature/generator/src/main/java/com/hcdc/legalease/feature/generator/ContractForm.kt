package com.hcdc.legalease.feature.generator

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.designsystem.component.textfield.PrimaryTextField
import com.hcdc.legalease.core.domain.generator.FieldError
import com.hcdc.legalease.core.domain.generator.FormField
import com.hcdc.legalease.core.domain.generator.FormStep
import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.InterestPeriod
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind

private typealias DraftUpdate = ((ContractDraft) -> ContractDraft) -> Unit

/** The stepper: one step of fields at a time, validated when the user taps Next. */
@Composable
internal fun ContractForm(
    state: EditorUiState,
    onUpdate: DraftUpdate,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val draft = state.draft
    val errors = state.errors
    val last = state.stepIndex == state.steps.lastIndex
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Text(
            text = stringResource(draft.type.templateName),
            style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics { heading() },
        )
        VerticalSpacer(5.dp)
        Text(
            text = stringResource(
                R.string.generator_step,
                state.stepIndex + 1,
                state.steps.size,
                stringResource(state.step.title(draft.type)),
            ),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
        )
        VerticalSpacer(8.dp)
        LinearProgressIndicator(
            progress = { (state.stepIndex + 1f) / state.steps.size },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
        VerticalSpacer(10.dp)
        HorizontalDivider(color = Color.Gray.copy(alpha = .3f), thickness = 1.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                FormStep.FIRST_PARTY -> PartyFields(draft.firstParty, first = true, errors) { change ->
                    onUpdate { it.copy(firstParty = change(it.firstParty)) }
                }
                FormStep.SECOND_PARTY -> {
                    PartyFields(draft.secondParty, first = false, errors) { change ->
                        onUpdate { it.copy(secondParty = change(it.secondParty)) }
                    }
                    CheckRow(
                        checked = draft.partiesOfLegalAge,
                        label = stringResource(R.string.generator_legal_age),
                        error = errors[FormField.PARTIES_OF_LEGAL_AGE],
                        onToggle = { checked -> onUpdate { it.copy(partiesOfLegalAge = checked) } },
                    )
                }
                FormStep.SUBJECT -> SubjectFields(draft, errors, onUpdate)
                FormStep.TERMS -> TermsFields(draft, errors, onUpdate)
                FormStep.SIGNING -> SigningFields(draft, errors, onUpdate)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                text = stringResource(R.string.generator_back),
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(if (last) R.string.generator_preview else R.string.generator_next),
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PartyFields(
    party: Party,
    first: Boolean,
    errors: Map<FormField, FieldError>,
    onChange: ((Party) -> Party) -> Unit,
) {
    fun error(firstField: FormField, secondField: FormField) = errors[if (first) firstField else secondField]
    Field(R.string.generator_field_name, party.name, error(FormField.FIRST_NAME, FormField.SECOND_NAME)) { v ->
        onChange { it.copy(name = v) }
    }
    Choice(
        label = stringResource(R.string.generator_field_civil_status),
        options = CivilStatus.entries,
        selected = party.civilStatus,
        text = { stringResource(it.labelRes) },
        error = error(FormField.FIRST_CIVIL_STATUS, FormField.SECOND_CIVIL_STATUS),
        onSelect = { status -> onChange { it.copy(civilStatus = status) } },
    )
    Field(
        R.string.generator_field_citizenship,
        party.citizenship,
        error(FormField.FIRST_CITIZENSHIP, FormField.SECOND_CITIZENSHIP),
    ) { v -> onChange { it.copy(citizenship = v) } }
    Field(
        R.string.generator_field_address,
        party.address,
        error(FormField.FIRST_ADDRESS, FormField.SECOND_ADDRESS),
        multiLine = true,
    ) { v -> onChange { it.copy(address = v) } }
    Field(
        R.string.generator_field_id_type,
        party.idType,
        error(FormField.FIRST_ID_TYPE, FormField.SECOND_ID_TYPE),
    ) { v -> onChange { it.copy(idType = v) } }
    Field(R.string.generator_field_id_number, party.idNumber, null) { v -> onChange { it.copy(idNumber = v) } }
}

@Composable
private fun SubjectFields(draft: ContractDraft, errors: Map<FormField, FieldError>, onUpdate: DraftUpdate) {
    if (draft.type == ContractType.SALE) {
        Choice(
            label = null,
            options = PropertyKind.entries,
            selected = draft.propertyKind,
            text = { stringResource(it.labelRes) },
            error = null,
            onSelect = { kind -> onUpdate { it.copy(propertyKind = kind) } },
        )
    }
    Field(
        descriptionLabel(draft.type, draft.propertyKind),
        draft.propertyDescription,
        errors[FormField.PROPERTY_DESCRIPTION],
        multiLine = true,
    ) { v -> onUpdate { it.copy(propertyDescription = v) } }
}

@Composable
private fun TermsFields(draft: ContractDraft, errors: Map<FormField, FieldError>, onUpdate: DraftUpdate) {
    Field(draft.type.amountLabel, draft.amount, errors[FormField.AMOUNT], KeyboardType.Decimal) { v ->
        onUpdate { it.copy(amount = v) }
    }
    when (draft.type) {
        ContractType.LEASE, ContractType.LEASE_OF_PERSONAL_PROPERTY ->
            Field(
                R.string.generator_field_deposit,
                draft.depositMonths,
                errors[FormField.DEPOSIT_MONTHS],
                KeyboardType.Number,
            ) { v -> onUpdate { it.copy(depositMonths = v) } }
        ContractType.LOAN -> LoanFields(draft, errors, onUpdate)
        ContractType.SALE -> Unit
    }
    if (draft.type != ContractType.SALE) {
        val loan = draft.type == ContractType.LOAN
        val startLabel = if (loan) R.string.generator_field_loan_release else R.string.generator_field_lease_start
        val endLabel = if (loan) R.string.generator_field_loan_due else R.string.generator_field_lease_end
        Field(startLabel, draft.startDate, errors[FormField.START_DATE]) { v -> onUpdate { it.copy(startDate = v) } }
        Field(endLabel, draft.endDate, errors[FormField.END_DATE]) { v -> onUpdate { it.copy(endDate = v) } }
    }
}

@Composable
private fun LoanFields(draft: ContractDraft, errors: Map<FormField, FieldError>, onUpdate: DraftUpdate) {
    Field(
        R.string.generator_field_interest,
        draft.interestRate,
        errors[FormField.INTEREST_RATE],
        KeyboardType.Decimal,
    ) { v -> onUpdate { it.copy(interestRate = v) } }
    Choice(
        label = null,
        options = InterestPeriod.entries,
        selected = draft.interestPeriod,
        text = { stringResource(it.labelRes) },
        error = null,
        onSelect = { period -> onUpdate { it.copy(interestPeriod = period) } },
    )
    Choice(
        label = null,
        options = PaymentSchedule.entries,
        selected = draft.paymentSchedule,
        text = { stringResource(it.labelRes) },
        error = null,
        onSelect = { schedule -> onUpdate { it.copy(paymentSchedule = schedule) } },
    )
    if (draft.paymentSchedule == PaymentSchedule.MONTHLY_INSTALLMENTS) {
        Field(
            R.string.generator_field_installments,
            draft.installments,
            errors[FormField.INSTALLMENTS],
            KeyboardType.Number,
        ) { v -> onUpdate { it.copy(installments = v) } }
    }
}

@Composable
private fun SigningFields(draft: ContractDraft, errors: Map<FormField, FieldError>, onUpdate: DraftUpdate) {
    Field(R.string.generator_field_place, draft.placeOfExecution, errors[FormField.PLACE_OF_EXECUTION]) { v ->
        onUpdate { it.copy(placeOfExecution = v) }
    }
    Field(R.string.generator_field_execution_date, draft.executionDate, errors[FormField.EXECUTION_DATE]) { v ->
        onUpdate { it.copy(executionDate = v) }
    }
    Field(R.string.generator_field_witness_one, draft.witnessOne, errors[FormField.WITNESS_ONE]) { v ->
        onUpdate { it.copy(witnessOne = v) }
    }
    Field(R.string.generator_field_witness_two, draft.witnessTwo, errors[FormField.WITNESS_TWO]) { v ->
        onUpdate { it.copy(witnessTwo = v) }
    }
}

@Composable
private fun Field(
    @StringRes label: Int,
    value: String,
    error: FieldError?,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiLine: Boolean = false,
    onChange: (String) -> Unit,
) {
    PrimaryTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(label),
        error = error?.let { stringResource(it.textRes) },
        keyboardType = keyboardType,
        singleLine = !multiLine,
        minLines = if (multiLine) 2 else 1,
    )
}

@Composable
private fun <T> Choice(
    label: String?,
    options: List<T>,
    selected: T?,
    text: @Composable (T) -> String,
    error: FieldError?,
    onSelect: (T) -> Unit,
) {
    Column {
        label?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        error?.let { ErrorText(stringResource(it.textRes)) }
    }
}

@Composable
internal fun CheckRow(checked: Boolean, label: String, error: FieldError?, onToggle: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
        }
        error?.let { ErrorText(stringResource(it.textRes)) }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}
