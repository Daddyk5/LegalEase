package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.CivilStatus
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.InterestPeriod
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.model.Party
import com.hcdc.legalease.core.domain.model.PaymentSchedule
import com.hcdc.legalease.core.domain.model.PropertyKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Storage format for the form behind a contract version, so it can be edited into a new version.
 * Enum names are stored; an unknown name makes the whole draft unreadable rather than guessed.
 */
internal object DraftJson {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PartyDto(
        val name: String,
        val civilStatus: String? = null,
        val citizenship: String,
        val address: String,
        val idType: String,
        val idNumber: String,
    )

    @Serializable
    private data class ClauseDto(val id: String, val title: String, val text: String, val source: String)

    @Serializable
    private data class Dto(
        val type: String,
        val firstParty: PartyDto,
        val secondParty: PartyDto,
        val partiesOfLegalAge: Boolean,
        val propertyKind: String,
        val propertyDescription: String,
        val amount: String,
        val depositMonths: String,
        val interestRate: String,
        val interestPeriod: String,
        val paymentSchedule: String,
        val installments: String,
        val startDate: String,
        val endDate: String,
        val placeOfExecution: String,
        val executionDate: String,
        val witnessOne: String,
        val witnessTwo: String,
        val optionalClauses: List<ClauseDto>,
    )

    fun encode(draft: ContractDraft): String = json.encodeToString(
        Dto.serializer(),
        with(draft) {
            Dto(
                type.name, firstParty.toDto(), secondParty.toDto(), partiesOfLegalAge, propertyKind.name,
                propertyDescription, amount, depositMonths, interestRate, interestPeriod.name, paymentSchedule.name,
                installments, startDate, endDate, placeOfExecution, executionDate, witnessOne, witnessTwo,
                optionalClauses.map { ClauseDto(it.id, it.title, it.text, it.source.name) },
            )
        },
    )

    fun decode(text: String): ContractDraft? = try {
        val d = json.decodeFromString(Dto.serializer(), text)
        ContractDraft(
            type = enumValueOf<ContractType>(d.type),
            firstParty = d.firstParty.toDomain(),
            secondParty = d.secondParty.toDomain(),
            partiesOfLegalAge = d.partiesOfLegalAge,
            propertyKind = enumValueOf<PropertyKind>(d.propertyKind),
            propertyDescription = d.propertyDescription,
            amount = d.amount,
            depositMonths = d.depositMonths,
            interestRate = d.interestRate,
            interestPeriod = enumValueOf<InterestPeriod>(d.interestPeriod),
            paymentSchedule = enumValueOf<PaymentSchedule>(d.paymentSchedule),
            installments = d.installments,
            startDate = d.startDate,
            endDate = d.endDate,
            placeOfExecution = d.placeOfExecution,
            executionDate = d.executionDate,
            witnessOne = d.witnessOne,
            witnessTwo = d.witnessTwo,
            optionalClauses = d.optionalClauses.map {
                OptionalClause(it.id, it.title, it.text, enumValueOf<OptionalClause.Source>(it.source))
            },
        )
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun Party.toDto() = PartyDto(name, civilStatus?.name, citizenship, address, idType, idNumber)

    private fun PartyDto.toDomain() = Party(
        name = name,
        civilStatus = civilStatus?.let { enumValueOf<CivilStatus>(it) },
        citizenship = citizenship,
        address = address,
        idType = idType,
        idNumber = idNumber,
    )
}
