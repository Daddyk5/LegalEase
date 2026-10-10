package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.repository.ContractRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Saves a generated contract as a new version; never overwrites an earlier one. */
class SaveContractVersionUseCase @Inject constructor(
    private val repository: ContractRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(
        contractId: String?,
        type: ContractType,
        title: String,
        body: String,
    ): GeneratedContract {
        val saved = repository.saveVersion(contractId, type, title, body)
        syncScheduler.requestSync()
        return saved
    }
}

class GetContractsUseCase @Inject constructor(private val repository: ContractRepository) {
    operator fun invoke(): Flow<List<GeneratedContract>> = repository.observeLatest()
}
